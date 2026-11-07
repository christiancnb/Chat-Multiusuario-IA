package br.unicesumar.chat.server;

import br.unicesumar.chat.agente.AgenteException;
import br.unicesumar.chat.agente.AgenteIA;
import br.unicesumar.chat.agente.Turno;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Agente de IA de mentira: responde um texto combinado, registra o que recebeu e pode ser
 * "segurado" (para simular uma IA lenta) ou configurado para falhar.
 */
public class AgenteFalso implements AgenteIA {

    private final List<List<Turno>> chamadas = new CopyOnWriteArrayList<>();
    private volatile String resposta = "resposta da IA";
    private volatile AgenteException falha;
    private volatile CountDownLatch portao;

    public AgenteFalso responderCom(String texto) {
        this.resposta = texto;
        return this;
    }

    public AgenteFalso falharCom(AgenteException e) {
        this.falha = e;
        return this;
    }

    /** As próximas chamadas ficam esperando até {@link #liberar()}. */
    public void segurar() {
        portao = new CountDownLatch(1);
    }

    public void liberar() {
        CountDownLatch p = portao;
        if (p != null) {
            p.countDown();
        }
    }

    /** Cada elemento é a conversa recebida em uma chamada. */
    public List<List<Turno>> chamadas() {
        return chamadas;
    }

    /** Texto da última fala (do usuário) da chamada de número {@code indice}. */
    public String perguntaDaChamada(int indice) {
        List<Turno> conversa = chamadas.get(indice);
        return conversa.get(conversa.size() - 1).texto();
    }

    @Override
    public String responder(List<Turno> conversa) throws AgenteException {
        chamadas.add(List.copyOf(conversa));
        CountDownLatch p = portao;
        if (p != null) {
            try {
                if (!p.await(10, TimeUnit.SECONDS)) {
                    throw new AgenteException("tempo esgotado no teste", "portão nunca liberado");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AgenteException("interrompido", "interrompido", e);
            }
        }
        AgenteException f = falha;
        if (f != null) {
            throw f;
        }
        return resposta;
    }

    @Override
    public String descricao() {
        return "agente falso (teste)";
    }
}
