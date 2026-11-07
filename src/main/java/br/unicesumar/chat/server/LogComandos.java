package br.unicesumar.chat.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Registro (log) de tudo o que os usuários digitam como comando e do motivo de cada erro.
 *
 * <p>Cada linha diz QUEM digitou, O QUE digitou e POR QUE falhou, por exemplo:</p>
 * <pre>
 * 2026-11-03 21:10:05 AVISO [ana] "/grupo entrar xyz" -> O grupo #xyz não existe. Use /grupos para ver os existentes.
 * </pre>
 *
 * <p>Níveis: {@code INFO} comando executado; {@code WARNING} comando recusado por erro do usuário
 * (uso errado, destino inexistente...); {@code SEVERE} falha inesperada do servidor.
 * O texto de mensagens comuns do chat NUNCA é gravado, só comandos que começam com {@code /}
 * e, em caso de erro, um trecho curto da linha.</p>
 */
public final class LogComandos {

    /** Nome do logger; qualquer configuração de log pode se ligar a ele. */
    public static final String NOME_LOGGER = "br.unicesumar.chat.comandos";
    private static final int TRECHO_MAXIMO = 80;

    private final Logger logger;

    public LogComandos() {
        this(Logger.getLogger(NOME_LOGGER));
    }

    /** Permite injetar outro logger (os testes capturam os registros com ele). */
    public LogComandos(Logger logger) {
        this.logger = logger;
    }

    public void executado(String usuario, String linha) {
        logger.log(Level.INFO, "[{0}] {1} -> ok", new Object[]{usuario, citar(linha)});
    }

    /** Erro do usuário: o motivo é o mesmo texto mostrado a ele. */
    public void recusado(String usuario, String linha, String motivo) {
        logger.log(Level.WARNING, "[{0}] {1} -> {2}", new Object[]{usuario, citar(linha), motivo});
    }

    /** Falha inesperada: registra a pilha para quem for depurar. */
    public void falhou(String usuario, String linha, Throwable causa) {
        LogRecord r = new LogRecord(Level.SEVERE, "[{0}] {1} -> falha inesperada: {2}");
        r.setParameters(new Object[]{usuario, citar(linha), causa.toString()});
        r.setThrown(causa);
        r.setLoggerName(logger.getName());
        logger.log(r);
    }

    /** Liga o log a um arquivo (criando a pasta se preciso), no formato de uma linha por registro. */
    public static FileHandler gravarEmArquivo(Path arquivo) throws IOException {
        Path pasta = arquivo.toAbsolutePath().getParent();
        if (pasta != null) {
            Files.createDirectories(pasta);
        }
        FileHandler handler = new FileHandler(arquivo.toString(), true);
        handler.setEncoding("UTF-8");
        handler.setFormatter(new FormatoLinha());
        handler.setLevel(Level.INFO);
        Logger logger = Logger.getLogger(NOME_LOGGER);
        logger.setLevel(Level.INFO);
        logger.addHandler(handler);
        return handler;
    }

    private static String citar(String linha) {
        String limpa = linha == null ? "" : linha.replaceAll("[\\r\\n]+", " ").trim();
        if (limpa.length() > TRECHO_MAXIMO) {
            limpa = limpa.substring(0, TRECHO_MAXIMO) + "...";
        }
        return "\"" + limpa + "\"";
    }

    /** Uma linha por registro: data, nível em português e mensagem (mais a pilha, se houver). */
    static final class FormatoLinha extends Formatter {
        @Override
        public String format(LogRecord r) {
            String nivel = r.getLevel() == Level.SEVERE ? "ERRO "
                    : r.getLevel() == Level.WARNING ? "AVISO" : "INFO ";
            String data = java.time.LocalDateTime.ofInstant(r.getInstant(), java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder sb = new StringBuilder(data).append(' ').append(nivel).append(' ')
                    .append(formatMessage(r)).append(System.lineSeparator());
            if (r.getThrown() != null) {
                java.io.StringWriter sw = new java.io.StringWriter();
                r.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
