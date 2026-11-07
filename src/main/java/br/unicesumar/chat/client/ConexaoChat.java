package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Usuario;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lado "rede" do cliente, sem nenhuma dependência de Swing: conecta ao servidor,
 * faz o handshake (envio do nome), envia texto e entrega as linhas recebidas a um
 * {@link Ouvinte} a partir de uma thread de leitura própria.
 *
 * <p>Separar isto da interface gráfica mantém a classe testável e evita que a tela
 * saiba qualquer detalhe do protocolo de sockets.</p>
 */
public class ConexaoChat {

    /** Recebe os eventos da conexão. Os métodos são chamados pela thread de leitura (não pela EDT). */
    public interface Ouvinte {
        void aoReceber(String linha);

        /** @param pedidoPeloUsuario {@code true} se foi {@link #desconectar()}; {@code false} se caiu/servidor encerrou */
        void aoDesconectar(boolean pedidoPeloUsuario);
    }

    /** Falha ao conectar ou ao se identificar; a mensagem já é própria para exibir ao usuário. */
    public static class FalhaConexao extends Exception {
        private static final long serialVersionUID = 1L;

        public FalhaConexao(String mensagem) {
            super(mensagem);
        }
    }

    private static final int TIMEOUT_MS = 5000;

    private final Ouvinte ouvinte;
    private Socket socket;
    private PrintWriter saida;
    private volatile boolean conectada;
    private volatile boolean encerradaPeloUsuario;

    public ConexaoChat(Ouvinte ouvinte) {
        this.ouvinte = Objects.requireNonNull(ouvinte);
    }

    /**
     * Conecta, identifica-se com o nome e aguarda a confirmação do servidor. Bloqueia
     * (até {@value #TIMEOUT_MS} ms por etapa), então não deve ser chamado na EDT.
     *
     * @return as linhas recebidas antes da confirmação (o histórico das últimas mensagens)
     * @throws FalhaConexao servidor inacessível, nome inválido ou já em uso etc.
     */
    public List<String> conectar(String host, int porta, String nome) throws FalhaConexao {
        if (conectada) {
            throw new IllegalStateException("Esta conexão já está ativa.");
        }
        if (host == null || host.isBlank()) {
            throw new FalhaConexao("Informe o endereço do servidor.");
        }
        final String nomeValido;
        try {
            nomeValido = new Usuario(nome).getNome();
        } catch (IllegalArgumentException e) {
            throw new FalhaConexao(e.getMessage());
        }

        Socket s = new Socket();
        boolean sucesso = false;
        try {
            s.connect(new InetSocketAddress(host.trim(), porta), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter escrita = new PrintWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);

            aguardarPedidoDeNome(entrada);
            escrita.println(nomeValido);
            List<String> previas = aguardarConfirmacao(entrada);

            s.setSoTimeout(0); // a partir daqui a leitura pode ficar bloqueada indefinidamente
            this.socket = s;
            this.saida = escrita;
            this.conectada = true;
            Thread leitor = new Thread(() -> ler(entrada), "leitor-servidor");
            leitor.setDaemon(true);
            leitor.start();
            sucesso = true;
            return previas;
        } catch (SocketTimeoutException e) {
            throw new FalhaConexao("O servidor não respondeu a tempo.");
        } catch (IllegalArgumentException e) {
            throw new FalhaConexao("Porta inválida: " + porta + ".");
        } catch (IOException e) {
            throw new FalhaConexao("Não foi possível conectar a " + host.trim() + ":" + porta + ".");
        } finally {
            if (!sucesso) {
                fecharQuieto(s);
            }
        }
    }

    /** Envia uma linha ao servidor. @return {@code false} se a conexão não está mais utilizável. */
    public boolean enviar(String texto) {
        if (!conectada) {
            return false;
        }
        saida.println(texto);
        return !saida.checkError();
    }

    /** Sai do chat: avisa o servidor (/sair) e encerra a conexão. Pode ser chamado mais de uma vez. */
    public void desconectar() {
        if (!conectada) {
            return;
        }
        encerradaPeloUsuario = true;
        saida.println("/sair");
        fecharQuieto(socket);
    }

    public boolean estaConectada() {
        return conectada;
    }

    // ---- internos ----

    private void aguardarPedidoDeNome(BufferedReader entrada) throws IOException, FalhaConexao {
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (linha.contains("Informe seu nome")) {
                return;
            }
        }
        throw new FalhaConexao("O servidor encerrou a conexão.");
    }

    private List<String> aguardarConfirmacao(BufferedReader entrada) throws IOException, FalhaConexao {
        List<String> previas = new ArrayList<>();
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (linha.startsWith("Conectado como ")) {
                return previas;
            }
            if (linha.startsWith("! ")) {
                throw new FalhaConexao(limparErro(linha));
            }
            previas.add(linha); // cabeçalho e mensagens do histórico
        }
        throw new FalhaConexao("O servidor encerrou a conexão.");
    }

    /** Remove o prefixo "! " e o convite para tentar de novo, que só faz sentido no console. */
    static String limparErro(String linha) {
        String texto = linha.startsWith("! ") ? linha.substring(2) : linha;
        for (String sufixo : new String[] {" Escolha outro:", " Tente novamente:"}) {
            if (texto.endsWith(sufixo)) {
                texto = texto.substring(0, texto.length() - sufixo.length());
            }
        }
        return texto.trim();
    }

    private void ler(BufferedReader entrada) {
        try {
            String linha;
            while ((linha = entrada.readLine()) != null) {
                ouvinte.aoReceber(linha);
            }
        } catch (IOException ignorada) {
            // conexão encerrada (por nós ou pela rede)
        } finally {
            boolean pedido = encerradaPeloUsuario;
            conectada = false;
            fecharQuieto(socket);
            ouvinte.aoDesconectar(pedido);
        }
    }

    private static void fecharQuieto(Socket s) {
        try {
            if (s != null) {
                s.close();
            }
        } catch (IOException ignorada) {
            // nada a fazer
        }
    }
}
