package br.unicesumar.chat.agente;

import br.unicesumar.chat.agente.Turno.Papel;
import br.unicesumar.chat.model.Mensagem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A UCI como serviço: recebe perguntas, consulta o {@link AgenteIA} em threads próprias e
 * devolve a resposta por um {@link Ouvinte}.
 *
 * <h2>Por que assíncrono</h2>
 * Uma chamada de IA leva segundos. Se fosse feita na thread do cliente, aquele usuário ficaria
 * "congelado"; se fosse feita dentro do lock da sala, o chat inteiro pararia. Por isso
 * {@link #perguntar} apenas enfileira o trabalho (não bloqueia) e o resultado volta depois.
 *
 * <h2>Limites e segurança</h2>
 * <ul>
 *   <li>no máximo uma pergunta em andamento por conversa (evita enchê-la de pedidos);</li>
 *   <li>pool de poucas threads e fila limitada: se lotar, a pergunta é recusada;</li>
 *   <li>memória da conversa limitada às últimas {@value #MAX_FALAS} falas;</li>
 *   <li>respostas são quebradas em mensagens de no máximo {@value #TAMANHO_PARTE} caracteres.</li>
 * </ul>
 *
 * <p>Os {@link Ouvinte ouvintes} são chamados em threads deste serviço, <b>sem</b> nenhum lock
 * dele retido, então podem tomar o lock da sala com segurança (não há inversão de locks).</p>
 */
public class ServicoUci implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ServicoUci.class.getName());

    public static final int MAX_FALAS = 20;
    public static final int TAMANHO_PARTE = 450;
    public static final int MAX_PARTES = 4;

    /** Resultado do pedido de resposta. */
    public enum Resultado { ACEITO, OCUPADO, SOBRECARREGADO }

    /** Recebe o desfecho de uma pergunta aceita. Exatamente um dos dois métodos é chamado. */
    public interface Ouvinte {
        void aoResponder(List<String> partes);

        /** @param mensagemAmigavel texto próprio para mostrar ao usuário */
        void aoFalhar(String mensagemAmigavel);
    }

    private final AgenteIA agente;
    private final ThreadPoolExecutor executor;
    private final Set<String> emAndamento = ConcurrentHashMap.newKeySet();
    private final Map<String, List<Turno>> memoria = new HashMap<>(); // protegido por "memoria"

    public ServicoUci(AgenteIA agente) {
        this(agente, 4, 20);
    }

    public ServicoUci(AgenteIA agente, int threads, int capacidadeDaFila) {
        this.agente = Objects.requireNonNull(agente);
        AtomicInteger numero = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacidadeDaFila),
                tarefa -> {
                    Thread t = new Thread(tarefa, "uci-" + numero.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
        this.executor.allowCoreThreadTimeOut(true);
    }

    public AgenteIA getAgente() {
        return agente;
    }

    /**
     * Pede uma resposta. Não bloqueia.
     *
     * @param conversa identifica a conversa (a memória e o limite de uma pergunta por vez valem por ela)
     * @param pergunta texto desta fala do usuário
     */
    public Resultado perguntar(String conversa, String pergunta, Ouvinte ouvinte) {
        return perguntar(conversa, pergunta, "", ouvinte);
    }

    /**
     * Como {@link #perguntar(String, String, Ouvinte)}, com uma instrução de estilo (ver {@link Personalidade})
     * que vai ao agente junto da pergunta mas NÃO fica na memória da conversa.
     */
    public Resultado perguntar(String conversa, String pergunta, String instrucao, Ouvinte ouvinte) {
        Objects.requireNonNull(instrucao);
        Objects.requireNonNull(conversa);
        Objects.requireNonNull(pergunta);
        Objects.requireNonNull(ouvinte);
        if (!emAndamento.add(conversa)) {
            return Resultado.OCUPADO;
        }
        try {
            executor.execute(() -> processar(conversa, pergunta, instrucao, ouvinte));
            return Resultado.ACEITO;
        } catch (RejectedExecutionException e) {
            emAndamento.remove(conversa);
            return Resultado.SOBRECARREGADO;
        }
    }

    /** Apaga a memória de uma conversa (ex.: quando o usuário sai do chat). */
    public void esquecer(String conversa) {
        synchronized (memoria) {
            memoria.remove(conversa);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    // ---- execução ----

    private void processar(String conversa, String pergunta, String instrucao, Ouvinte ouvinte) {
        List<String> partes = null;
        String falha = null;
        try {
            List<Turno> contexto = contextoCom(conversa, new Turno(Papel.USUARIO, instrucao + pergunta));
            String resposta = agente.responder(contexto);
            guardar(conversa, pergunta, resposta);
            partes = dividir(resposta);
        } catch (AgenteException e) {
            LOG.log(Level.WARNING, "A UCI não conseguiu responder: " + e.getDetalhe());
            falha = e.getMessage();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro inesperado na UCI.", e);
            falha = "A UCI teve um problema inesperado.";
        }

        try {
            if (partes != null) {
                ouvinte.aoResponder(partes);
            } else {
                ouvinte.aoFalhar(falha);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro ao entregar a resposta da UCI.", e);
        } finally {
            emAndamento.remove(conversa);
        }
    }

    private List<Turno> contextoCom(String conversa, Turno nova) {
        List<Turno> contexto = new ArrayList<>();
        synchronized (memoria) {
            contexto.addAll(memoria.getOrDefault(conversa, List.of()));
        }
        contexto.add(nova);
        return contexto;
    }

    /** Só guarda o par pergunta/resposta depois do sucesso, então uma falha não deixa "buracos" na memória. */
    private void guardar(String conversa, String pergunta, String resposta) {
        synchronized (memoria) {
            List<Turno> falas = memoria.computeIfAbsent(conversa, k -> new ArrayList<>());
            falas.add(new Turno(Papel.USUARIO, pergunta));
            falas.add(new Turno(Papel.AGENTE, resposta));
            while (falas.size() > MAX_FALAS) {
                falas.remove(0);
                falas.remove(0);
            }
        }
    }

    // ---- formatação da resposta ----

    /**
     * Transforma a resposta em mensagens válidas para o chat: uma linha só (espaços e quebras
     * viram um espaço), no máximo {@value #TAMANHO_PARTE} caracteres cada (corta em espaços) e no
     * máximo {@value #MAX_PARTES} partes (o excedente é substituído por "…").
     */
    static List<String> dividir(String bruto) {
        String texto = bruto == null ? "" : bruto.replaceAll("\\s+", " ").trim();
        if (texto.isEmpty()) {
            return List.of("(sem resposta)");
        }
        List<String> partes = new ArrayList<>();
        while (!texto.isEmpty() && partes.size() < MAX_PARTES) {
            if (texto.length() <= TAMANHO_PARTE) {
                partes.add(texto);
                texto = "";
                break;
            }
            int corte = texto.lastIndexOf(' ', TAMANHO_PARTE);
            if (corte < TAMANHO_PARTE / 2) {
                corte = TAMANHO_PARTE; // palavra enorme: corta no limite
            }
            partes.add(texto.substring(0, corte).trim());
            texto = texto.substring(corte).trim();
        }
        if (!texto.isEmpty()) { // sobrou texto além do máximo de partes
            int ultima = partes.size() - 1;
            partes.set(ultima, partes.get(ultima) + "…");
        }
        // Garantia final: nenhuma parte pode violar o limite do modelo de mensagem.
        for (String parte : partes) {
            if (parte.length() > Mensagem.TAMANHO_MAXIMO) {
                throw new IllegalStateException("Parte maior que o limite de mensagem.");
            }
        }
        return partes;
    }
}
