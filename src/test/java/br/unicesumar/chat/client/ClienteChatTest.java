package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O cliente de terminal precisa converter o que o teclado entrega (ex.: cp850 do Windows) para UTF-8. */
class ClienteChatTest {

    /** Servidor de mentira: guarda as linhas recebidas e responde com uma saudação em UTF-8. */
    private static CompletableFuture<List<String>> servidorFalso(ServerSocket servidor) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> linhas = new ArrayList<>();
            try (Socket s = servidor.accept()) {
                var saida = new java.io.PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
                saida.println("Bem-vindo ao chat! Informe seu nome:");
                var entrada = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String linha;
                while ((linha = entrada.readLine()) != null) {
                    linhas.add(linha);
                    if (linha.equals("/sair")) {
                        saida.println("Até logo!");
                        break;
                    }
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return linhas;
        });
    }

    @Test
    void acentosDigitadosEmCodificacaoDoTerminalChegamEmUtf8() throws Exception {
        Charset terminal = Charset.forName("windows-1252");
        try (ServerSocket servidor = new ServerSocket(0)) {
            CompletableFuture<List<String>> recebido = servidorFalso(servidor);
            ByteArrayOutputStream bytesTela = new ByteArrayOutputStream();
            PrintStream tela = new PrintStream(bytesTela, true, terminal);
            ByteArrayInputStream teclado = new ByteArrayInputStream("José\nação rápida\n/sair\n".getBytes(terminal));

            new ClienteChat("localhost", servidor.getLocalPort()).executar(teclado, terminal, tela);

            assertEquals(List.of("José", "ação rápida", "/sair"), recebido.get(5, TimeUnit.SECONDS));
            assertTrue(bytesTela.toString(terminal).contains("Até logo!"),
                    "o que o servidor envia deve aparecer corretamente no terminal");
        }
    }

    @Test
    void charsetDoTerminalNuncaEhNulo() {
        assertTrue(ClienteChat.charsetDoTerminal() != null);
    }

    @Test
    void exportarGravaATelaEOQueFoiDigitadoSemEnviarOComandoAoServidor() throws Exception {
        java.nio.file.Path pasta = java.nio.file.Files.createTempDirectory("terminal-export");
        java.nio.file.Path arquivo = pasta.resolve("saida.txt");
        try (ServerSocket servidor = new ServerSocket(0)) {
            CompletableFuture<List<String>> recebido = servidorFalso(servidor);
            ByteArrayOutputStream bytesTela = new ByteArrayOutputStream();
            // a saudação do servidor chega por outra thread: o "teclado" só continua depois que ela apareceu
            java.util.concurrent.CountDownLatch saudacao = new java.util.concurrent.CountDownLatch(1);
            PrintStream tela = new PrintStream(bytesTela, true, StandardCharsets.UTF_8) {
                @Override
                public void println(String linha) {
                    super.println(linha);
                    if (linha.contains("Bem-vindo")) {
                        saudacao.countDown();
                    }
                }
            };
            java.io.InputStream inicio = new ByteArrayInputStream("ana\nolá, pessoal\n".getBytes(StandardCharsets.UTF_8));
            java.io.InputStream fim = new java.io.InputStream() {
                private java.io.InputStream resto;

                @Override
                public int read() throws java.io.IOException {
                    if (resto == null) {
                        try {
                            if (!saudacao.await(5, TimeUnit.SECONDS)) {
                                throw new java.io.IOException("a saudação do servidor não chegou");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new java.io.IOException(e);
                        }
                        resto = new ByteArrayInputStream(("/exportar " + arquivo + "\n/sair\n").getBytes(StandardCharsets.UTF_8));
                    }
                    return resto.read();
                }
            };

            new ClienteChat("localhost", servidor.getLocalPort()).executar(
                    new java.io.SequenceInputStream(inicio, fim), StandardCharsets.UTF_8, tela);

            assertEquals(List.of("ana", "olá, pessoal", "/sair"), recebido.get(5, TimeUnit.SECONDS),
                    "o /exportar não vai para o servidor");
            String conteudo = java.nio.file.Files.readString(arquivo, StandardCharsets.UTF_8);
            assertTrue(conteudo.contains("Bem-vindo ao chat!"), conteudo);
            assertTrue(conteudo.contains("Você: olá, pessoal"), conteudo);
            assertTrue(bytesTela.toString(StandardCharsets.UTF_8).contains("Conversa exportada para"));
        }
    }

    @Test
    void exportarComCaminhoInvalidoMostraOMotivoSemDerrubarOCliente() {
        java.nio.file.Path pasta;
        try {
            pasta = java.nio.file.Files.createTempDirectory("terminal-export");
            java.nio.file.Path ocupado = pasta.resolve("arquivo");
            java.nio.file.Files.writeString(ocupado, "x");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintStream tela = new PrintStream(bytes, true, StandardCharsets.UTF_8);

            ClienteChat.exportar("/exportar " + ocupado.resolve("filho.txt"), List.of("linha"), tela);

            String saida = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(saida.startsWith("! Não foi possível exportar para"), saida);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
