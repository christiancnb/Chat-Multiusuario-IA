package br.unicesumar.chat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cliente de console: conecta ao servidor, envia o que o usuário digita e
 * exibe tudo o que chega (uma thread dedicada lê do socket, para que mensagens
 * recebidas apareçam enquanto o usuário digita).
 *
 * <p>Uso: {@code java -cp chat.jar br.unicesumar.chat.client.ClienteChat [host] [porta]}</p>
 */
public class ClienteChat {

    private final String host;
    private final int porta;

    public ClienteChat(String host, int porta) {
        this.host = host;
        this.porta = porta;
    }

    /**
     * Charset do terminal. O servidor fala UTF-8, mas o cmd do Windows usa outra página de códigos
     * (ex.: cp850/Cp1252): ler o teclado como UTF-8 estraga acentos (o nome "José" vira "Jos?" e é
     * recusado como inválido). Por isso o cliente usa o charset do console quando houver um.
     */
    static Charset charsetDoTerminal() {
        java.io.Console console = System.console();
        if (console != null) {
            return console.charset();
        }
        String nativo = System.getProperty("native.encoding");
        try {
            return nativo != null ? Charset.forName(nativo) : Charset.defaultCharset();
        } catch (IllegalArgumentException e) {
            return Charset.defaultCharset();
        }
    }

    public void executar(InputStream teclado, PrintStream tela) throws IOException, InterruptedException {
        executar(teclado, StandardCharsets.UTF_8, tela);
    }

    /** @param charsetTeclado codificação em que o teclado entrega os caracteres */
    public void executar(InputStream teclado, Charset charsetTeclado, PrintStream tela)
            throws IOException, InterruptedException {
        try (Socket socket = new Socket(host, porta)) {
            BufferedReader doServidor = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter paraServidor = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Tudo o que aparece na tela (e o que o usuário digitou) fica guardado para o /exportar.
            List<String> registro = new CopyOnWriteArrayList<>();

            Thread leitor = new Thread(() -> {
                try {
                    String linha;
                    while ((linha = doServidor.readLine()) != null) {
                        registro.add(linha);
                        tela.println(linha);
                    }
                } catch (IOException ignorada) {
                    // conexão encerrada
                }
                tela.println("[Conexão encerrada. Pressione Enter para sair.]");
            }, "leitor-servidor");
            leitor.setDaemon(true);
            leitor.start();

            BufferedReader entradaUsuario = new BufferedReader(
                    new InputStreamReader(teclado, charsetTeclado));
            String linha;
            while ((linha = entradaUsuario.readLine()) != null) {
                if (ehExportar(linha)) { // comando só do cliente: o servidor não participa
                    exportar(linha, registro, tela);
                    continue;
                }
                if (!linha.isBlank() && !linha.startsWith("/")) {
                    registro.add("Você: " + linha.trim());
                }
                paraServidor.println(linha);
                if (paraServidor.checkError() || linha.trim().equalsIgnoreCase("/sair")) {
                    break;
                }
            }
            leitor.join(2000); // aguarda a despedida do servidor
        }
    }

    private static boolean ehExportar(String linha) {
        String t = linha.trim().toLowerCase(java.util.Locale.ROOT);
        return t.equals("/exportar") || t.startsWith("/exportar ");
    }

    /** {@code /exportar [arquivo]}: salva o que apareceu até agora em um arquivo de texto (UTF-8). */
    static void exportar(String comando, List<String> registro, PrintStream tela) {
        String nome = comando.trim().substring("/exportar".length()).trim();
        LocalDateTime agora = LocalDateTime.now();
        Path arquivo = Path.of(nome.isEmpty() ? Exportacao.nomeSugerido("terminal", agora) : nome);
        try {
            Path gravado = Exportacao.salvar(arquivo, "Terminal", String.join("\n", registro), agora);
            tela.println("Conversa exportada para " + gravado.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            tela.println("! Não foi possível exportar para '" + arquivo + "': " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        Charset terminal = charsetDoTerminal();
        PrintStream tela = new PrintStream(System.out, true, terminal);
        new ClienteChat(host, porta).executar(System.in, terminal, tela);
    }
}
