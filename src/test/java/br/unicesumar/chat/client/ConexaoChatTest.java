package br.unicesumar.chat.client;

import br.unicesumar.chat.client.ConexaoChat.FalhaConexao;
import br.unicesumar.chat.server.RepositorioEmMemoria;
import br.unicesumar.chat.server.SalaChat;
import br.unicesumar.chat.server.Servidor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa a lógica de rede do cliente (a base da interface gráfica) contra um servidor
 * real em localhost, sem abrir nenhuma janela.
 */
@Timeout(60)
class ConexaoChatTest {

    private Servidor servidor;
    private final List<ConexaoChat> abertas = new ArrayList<>();

    @BeforeEach
    void subirServidor() throws IOException {
        RepositorioEmMemoria repositorio = new RepositorioEmMemoria();
        servidor = new Servidor(0, new SalaChat(repositorio, 10));
        servidor.abrir();
        Thread aceitador = new Thread(servidor::aceitarConexoes, "aceitador-teste");
        aceitador.setDaemon(true);
        aceitador.start();
    }

    @AfterEach
    void derrubar() {
        abertas.forEach(ConexaoChat::desconectar);
        servidor.parar();
    }

    @Test
    void conectaEReceboMensagensDeOutroCliente() throws Exception {
        Registro ana = new Registro();
        Registro bia = new Registro();
        conectar(ana, "ana");
        ConexaoChat conexaoBia = conectar(bia, "bia");

        conexaoBia.enviar("olá, ana!");

        assertTrue(ana.esperarLinhaContendo("bia: olá, ana!"));
    }

    @Test
    void novoClienteRecebeOHistoricoComoPreviasDaConexao() throws Exception {
        Registro ana = new Registro();
        ConexaoChat conexaoAna = conectar(ana, "ana");
        conexaoAna.enviar("primeira");
        conexaoAna.enviar("segunda");
        conexaoAna.enviar("/usuarios");
        assertTrue(ana.esperarLinhaContendo("Conectados:")); // as duas anteriores já foram processadas

        List<String> previas = new ConexaoChat(new Registro()).conectar("localhost", servidor.getPortaLocal(), "bia");

        assertTrue(previas.stream().anyMatch(l -> l.endsWith("ana: primeira")));
        assertTrue(previas.stream().anyMatch(l -> l.endsWith("ana: segunda")));
    }

    @Test
    void nomeEmUsoGeraFalhaComMensagemLegivel() throws Exception {
        conectar(new Registro(), "ana");

        FalhaConexao falha = assertThrows(FalhaConexao.class,
                () -> new ConexaoChat(new Registro()).conectar("localhost", servidor.getPortaLocal(), "ANA"));

        assertTrue(falha.getMessage().contains("já está em uso"));
        assertFalse(falha.getMessage().endsWith(":"), "o convite do console deve ser removido");
    }

    @Test
    void nomeInvalidoEhRecusadoAntesDeUsarARede() {
        FalhaConexao falha = assertThrows(FalhaConexao.class,
                () -> new ConexaoChat(new Registro()).conectar("localhost", servidor.getPortaLocal(), "a b"));
        assertTrue(falha.getMessage().startsWith("Nome inválido"));
    }

    @Test
    void servidorInacessivelGeraFalhaClara() throws IOException {
        int portaFechada = servidor.getPortaLocal();
        servidor.parar();

        FalhaConexao falha = assertThrows(FalhaConexao.class,
                () -> new ConexaoChat(new Registro()).conectar("localhost", portaFechada, "ana"));

        assertTrue(falha.getMessage().startsWith("Não foi possível conectar"));
    }

    @Test
    void desconectarAvisaOsOutrosENotificaComoPedidoPeloUsuario() throws Exception {
        Registro ana = new Registro();
        Registro bia = new Registro();
        conectar(ana, "ana");
        ConexaoChat conexaoBia = conectar(bia, "bia");

        conexaoBia.desconectar();

        assertEquals(Boolean.TRUE, bia.esperarDesconexao());
        assertTrue(ana.esperarLinhaContendo("bia saiu do chat"));
        assertFalse(conexaoBia.estaConectada());
        assertFalse(conexaoBia.enviar("fantasma"));
    }

    @Test
    void quedaDoServidorENotificadaComoNaoPedidaPeloUsuario() throws Exception {
        // Servidor falso: cumpre o handshake e em seguida derruba a conexão.
        try (ServerSocket falso = new ServerSocket(0)) {
            Thread t = new Thread(() -> {
                try (Socket s = falso.accept()) {
                    PrintWriter saida = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
                    BufferedReader entrada = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    saida.println("Bem-vindo ao chat! Informe seu nome:");
                    String nome = entrada.readLine();
                    saida.println("Conectado como " + nome + ". Comandos: /usuarios, /ajuda, /sair");
                } catch (IOException ignorada) {
                    // o teste só precisa que a conexão seja encerrada
                }
            });
            t.setDaemon(true);
            t.start();

            Registro registro = new Registro();
            ConexaoChat conexao = new ConexaoChat(registro);
            conexao.conectar("localhost", falso.getLocalPort(), "ana");

            assertEquals(Boolean.FALSE, registro.esperarDesconexao());
            assertFalse(conexao.estaConectada());
        }
    }

    @Test
    void limparErroRemoveOPrefixoEOConviteDoConsole() {
        assertEquals("O nome 'x' já está em uso.",
                ConexaoChat.limparErro("! O nome 'x' já está em uso. Escolha outro:"));
        assertEquals("Nome inválido.", ConexaoChat.limparErro("! Nome inválido. Tente novamente:"));
    }

    // ---- utilidades ----

    private ConexaoChat conectar(Registro registro, String nome) throws FalhaConexao {
        ConexaoChat conexao = new ConexaoChat(registro);
        conexao.conectar("localhost", servidor.getPortaLocal(), nome);
        abertas.add(conexao);
        return conexao;
    }

    /** Ouvinte que guarda os eventos em filas para o teste aguardar com timeout. */
    private static final class Registro implements ConexaoChat.Ouvinte {
        private final BlockingQueue<String> linhas = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> desconexoes = new LinkedBlockingQueue<>();

        @Override
        public void aoReceber(String linha) {
            linhas.add(linha);
        }

        @Override
        public void aoDesconectar(boolean pedidoPeloUsuario) {
            desconexoes.add(pedidoPeloUsuario);
        }

        boolean esperarLinhaContendo(String trecho) throws InterruptedException {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < limite) {
                String linha = linhas.poll(200, TimeUnit.MILLISECONDS);
                if (linha != null && linha.contains(trecho)) {
                    return true;
                }
            }
            return false;
        }

        Boolean esperarDesconexao() throws InterruptedException {
            return desconexoes.poll(5, TimeUnit.SECONDS);
        }
    }
}
