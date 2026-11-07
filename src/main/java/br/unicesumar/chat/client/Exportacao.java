package br.unicesumar.chat.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Salva uma conversa em arquivo de texto (UTF-8). Lógica pura, usada pela janela e pelo terminal. */
final class Exportacao {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter PARA_NOME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Exportacao() {
    }

    /** Cabeçalho + conteúdo, no formato gravado no arquivo. */
    static String montar(String titulo, String conteudo, LocalDateTime agora) {
        String corpo = conteudo == null ? "" : conteudo.stripTrailing();
        return "Chat Multiusuário — " + titulo + System.lineSeparator()
                + "Exportado em " + DATA_HORA.format(agora) + System.lineSeparator()
                + "----------------------------------------" + System.lineSeparator()
                + (corpo.isEmpty() ? "(conversa vazia)" : corpo.replace("\n", System.lineSeparator()))
                + System.lineSeparator();
    }

    /** Nome sugerido, ex.: {@code chat-estudo-20261103-201500.txt} (só letras, números, '-' e '_'). */
    static String nomeSugerido(String titulo, LocalDateTime agora) {
        String limpo = titulo.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_-]+", "");
        if (limpo.isEmpty()) {
            limpo = "conversa";
        }
        return "chat-" + limpo + "-" + PARA_NOME.format(agora) + ".txt";
    }

    /**
     * Grava o arquivo (criando pastas se preciso) e devolve o caminho.
     *
     * @throws IOException se não for possível gravar (o chamador mostra o motivo ao usuário)
     */
    static Path salvar(Path arquivo, String titulo, String conteudo, LocalDateTime agora) throws IOException {
        Path pasta = arquivo.toAbsolutePath().getParent();
        if (pasta != null) {
            Files.createDirectories(pasta);
        }
        return Files.writeString(arquivo, montar(titulo, conteudo, agora), StandardCharsets.UTF_8);
    }
}
