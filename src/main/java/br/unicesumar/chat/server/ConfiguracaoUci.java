package br.unicesumar.chat.server;

import br.unicesumar.chat.agente.AgenteCompativel;
import br.unicesumar.chat.agente.AgenteException;
import br.unicesumar.chat.agente.AgenteIA;
import br.unicesumar.chat.agente.AgenteLocal;
import br.unicesumar.chat.agente.Turno;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Onde a UCI busca sua configuração: primeiro as variáveis de ambiente e, se não houver,
 * o arquivo {@code uci.properties} na pasta onde o servidor foi iniciado (mais fácil no
 * Windows/IDE, onde variáveis de ambiente costumam não chegar ao programa).
 * Também faz um teste rápido do agente ao subir, para o log dizer se a IA está funcionando.
 */
final class ConfiguracaoUci {

    static final String ARQUIVO_PADRAO = "uci.properties";

    private ConfiguracaoUci() {
    }

    /** Ambiente primeiro; arquivo como reserva. Arquivo ausente ou ilegível é ignorado (com aviso no log). */
    static Function<String, String> combinar(Function<String, String> ambiente, Path arquivo, Logger log) {
        Properties props = new Properties();
        if (Files.isRegularFile(arquivo)) {
            try (Reader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
                props.load(leitor);
                log.info("Configuração da UCI lida de " + arquivo.toAbsolutePath());
            } catch (IOException | IllegalArgumentException e) {
                log.warning("Não consegui ler " + arquivo.toAbsolutePath() + ": " + e.getMessage());
            }
        } else {
            log.info("Arquivo de configuração da UCI (opcional) NÃO encontrado em " + arquivo.toAbsolutePath()
                    + " (confira a pasta e o nome: no Windows, 'uci.properties.txt' é um erro comum).");
        }
        return nome -> {
            String valor = ambiente.apply(nome);
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
            String doArquivo = props.getProperty(nome);
            return doArquivo == null || doArquivo.isBlank() ? null : doArquivo.trim();
        };
    }

    /** Em segundo plano, pergunta algo simples ao agente e registra no log o resultado (sem travar a subida). */
    static void testarEmSegundoPlano(AgenteIA agente, Logger log) {
        if (agente instanceof AgenteLocal) {
            log.warning("UCI em MODO LOCAL (offline). Para usar IA, defina UCI_PROVIDER (ex.: ollama) "
                    + "no ambiente ou no arquivo " + ARQUIVO_PADRAO + " e reinicie o servidor.");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                String r = agente.responder(List.of(new Turno(Turno.Papel.USUARIO, "Responda apenas: ok")));
                log.info("Teste da UCI: FUNCIONOU (" + agente.descricao() + "). Resposta: " + resumir(r));
            } catch (AgenteException e) {
                log.warning("Teste da UCI: FALHOU (" + agente.descricao() + "). Motivo: " + e.getDetalhe());
                if (agente instanceof AgenteCompativel compativel && e.getDetalhe().contains("HTTP 404")) {
                    List<String> modelos = compativel.listarModelos();
                    if (!modelos.isEmpty()) {
                        log.warning("Modelos disponíveis neste serviço: " + String.join(", ", modelos)
                                + ". Escolha um e coloque em UCI_MODEL no uci.properties.");
                    }
                }
            } catch (RuntimeException e) {
                log.warning("Teste da UCI: FALHOU (" + agente.descricao() + "). Erro inesperado: " + e);
            }
        }, "teste-uci");
        t.setDaemon(true);
        t.start();
    }

    private static String resumir(String s) {
        String limpo = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return limpo.length() > 80 ? limpo.substring(0, 80) + "..." : limpo;
    }
}
