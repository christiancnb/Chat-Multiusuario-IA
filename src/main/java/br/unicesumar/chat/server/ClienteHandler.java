package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Usuario;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Atende UMA conexão de cliente, em sua própria thread.
 *
 * <p>Protocolo (texto, uma linha por mensagem, UTF-8):
 * o primeiro texto enviado pelo cliente é o seu nome; depois, cada linha é uma
 * mensagem de chat ou um comando ({@code /usuarios}, {@code /ajuda}, {@code /sair}).</p>
 *
 * <p>Cada handler usa duas threads: esta (leitura do socket) e uma "escritora"
 * que esvazia a fila de saída. Assim, {@link #enviar(String)} nunca bloqueia a sala
 * por causa de um cliente lento, e a escrita no socket nunca é feita por duas
 * threads ao mesmo tempo.</p>
 */
public class ClienteHandler implements Runnable, Destinatario {

    private static final Logger LOG = Logger.getLogger(ClienteHandler.class.getName());
    private static final int CAPACIDADE_FILA = 500;
    private static final int MAX_TENTATIVAS_NOME = 5;

    /** Sentinela comparada por identidade (==) para encerrar a thread escritora. */
    @SuppressWarnings("StringOperationCanBeSimplified")
    private static final String FIM = new String("<fim-da-fila>");

    private final Socket socket;
    private final SalaChat sala;
    private final ComandosChat comandos;
    private final BlockingQueue<String> filaSaida = new LinkedBlockingQueue<>(CAPACIDADE_FILA);

    private volatile Usuario usuario;
    private volatile boolean ativo = true;
    private volatile boolean controle;
    private PrintWriter saida; // criado em run() antes de iniciar a thread escritora

    public ClienteHandler(Socket socket, SalaChat sala) {
        this.socket = socket;
        this.sala = sala;
        this.comandos = new ComandosChat(sala);
    }

    @Override
    public String getNome() {
        Usuario u = usuario;
        return u == null ? "?" : u.getNome();
    }

    @Override
    public boolean aceitaControle() {
        return controle;
    }

    @Override
    public void ativarControle() {
        controle = true;
    }

    @Override
    public boolean enviar(String linha) {
        if (!ativo) {
            return false;
        }
        if (filaSaida.offer(linha)) {
            return true;
        }
        LOG.warning("Fila de saída cheia para " + getNome() + "; desconectando o cliente lento.");
        fecharSocket();
        return false;
    }

    @Override
    public void run() {
        Thread escritor = null;
        boolean registrado = false;
        try {
            saida = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            escritor = new Thread(this::escrever, "escritor-" + Thread.currentThread().getName());
            escritor.setDaemon(true);
            escritor.start();

            enviar("Bem-vindo ao chat! Informe seu nome:");
            registrado = identificar(entrada);
            if (registrado) {
                conversar(entrada);
            }
        } catch (IOException e) {
            // Desconexão abrupta do cliente: situação normal, não derruba nada.
            LOG.log(Level.FINE, "Conexão de " + getNome() + " encerrada: " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro inesperado no atendimento de " + getNome(), e);
        } finally {
            encerrar(registrado, escritor);
        }
    }

    /** Pede o nome até ser válido e estar livre. @return false se o cliente desistiu/errou demais. */
    private boolean identificar(BufferedReader entrada) throws IOException {
        for (int tentativa = 0; tentativa < MAX_TENTATIVAS_NOME; tentativa++) {
            String linha = entrada.readLine();
            if (linha == null) {
                return false;
            }
            try {
                usuario = new Usuario(linha);
            } catch (IllegalArgumentException e) {
                enviar("! " + e.getMessage() + " Tente novamente:");
                LOG.log(Level.WARNING, "Nome recusado na entrada (\"{0}\"): {1}", new Object[]{linha, e.getMessage()});
                continue;
            }
            if (sala.entrar(this)) {
                return true;
            }
            enviar("! O nome '" + usuario.getNome() + "' já está em uso. Escolha outro:");
            LOG.log(Level.WARNING, "Nome recusado na entrada (\"{0}\"): já está em uso.", usuario.getNome());
        }
        enviar("! Tentativas demais. Encerrando a conexão.");
        return false;
    }

    private void conversar(BufferedReader entrada) throws IOException {
        enviar("Conectado como " + getNome() + ". Comandos: /ajuda, /usuarios, /sair");
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (comandos.executar(this, linha) == ComandosChat.Resultado.SAIR) {
                return;
            }
        }
    }

    /** Thread escritora: tira linhas da fila e as escreve no socket, uma por vez. */
    private void escrever() {
        try {
            while (true) {
                String linha = filaSaida.take();
                if (linha == FIM) {
                    break;
                }
                saida.println(linha); // autoflush
                if (saida.checkError()) {
                    fecharSocket();
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void encerrar(boolean registrado, Thread escritor) {
        if (registrado) {
            sala.sair(this);
        }
        ativo = false;
        if (!filaSaida.offer(FIM)) {
            filaSaida.clear();
            filaSaida.offer(FIM);
        }
        if (escritor != null) {
            try {
                escritor.join(2000); // dá tempo de enviar a despedida
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        fecharSocket();
        LOG.info("Cliente " + getNome() + " desconectado.");
    }

    private void fecharSocket() {
        ativo = false;
        try {
            socket.close();
        } catch (IOException ignorada) {
            // nada a fazer
        }
    }
}
