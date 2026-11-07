package br.unicesumar.chat.server;

import br.unicesumar.chat.persistence.BancoDeTeste;
import br.unicesumar.chat.persistence.JdbcMensagemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de ponta a ponta: servidor real (porta efêmera em localhost), banco H2
 * em memória e clientes via socket. Reproduz o cenário do vídeo: 3+ clientes.
 */
@Timeout(60)
class ServidorIntegracaoTest {

    private JdbcMensagemRepository repositorio;
    private Servidor servidor;
    private final List<ClienteDeTeste> abertos = new ArrayList<>();

    @BeforeEach
    void subirServidor() throws IOException {
        repositorio = BancoDeTeste.novoRepositorio();
        servidor = new Servidor(0, new SalaChat(repositorio, 10));
        servidor.abrir();
        Thread aceitador = new Thread(servidor::aceitarConexoes, "aceitador-teste");
        aceitador.setDaemon(true);
        aceitador.start();
    }

    @AfterEach
    void derrubarTudo() {
        abertos.forEach(ClienteDeTeste::fechar);
        servidor.parar();
    }

    @Test
    void tresClientesSimultaneosTrocamMensagens() throws IOException {
        ClienteDeTeste ana = conectar("ana");
        ClienteDeTeste bia = conectar("bia");
        ClienteDeTeste caio = conectar("caio");

        ana.enviar("olá, pessoal");
        bia.lerAte("ana: olá, pessoal");
        caio.lerAte("ana: olá, pessoal");

        caio.enviar("e aí!");
        ana.lerAte("caio: e aí!");
        bia.lerAte("caio: e aí!");
    }

    @Test
    void desconexaoAbruptaNaoDerrubaOServidorNemTravaOsOutros() throws IOException {
        ClienteDeTeste ana = conectar("ana");
        ClienteDeTeste bia = conectar("bia");
        ClienteDeTeste caio = conectar("caio");

        bia.fechar(); // cai sem enviar /sair
        ana.lerAte("bia saiu do chat");

        ana.enviar("ainda funciona?");
        caio.lerAte("ana: ainda funciona?");

        ClienteDeTeste dani = conectar("dani"); // servidor continua aceitando conexões
        ana.enviar("bem-vinda, dani");
        dani.lerAte("ana: bem-vinda, dani");
    }

    @Test
    void novoClienteRecebeOHistoricoGravadoNoBanco() throws IOException {
        ClienteDeTeste ana = conectar("ana");
        ana.enviar("primeira");
        ana.enviar("segunda");
        ana.enviar("/usuarios");
        ana.lerAte("Conectados:"); // garante que as duas anteriores já foram processadas

        assertEquals(2, repositorio.buscarUltimas(10).size());

        // O histórico chega antes da mensagem "Conectado como...", que conectar() aguarda;
        // por isso ele já está em linhasLidas() quando conectar() retorna.
        ClienteDeTeste bia = conectar("bia");
        assertTrue(bia.linhasLidas().stream().anyMatch(l -> l.endsWith("ana: primeira")));
        assertTrue(bia.linhasLidas().stream().anyMatch(l -> l.endsWith("ana: segunda")));
        int primeira = indiceQueTermina(bia.linhasLidas(), "ana: primeira");
        int segunda = indiceQueTermina(bia.linhasLidas(), "ana: segunda");
        assertTrue(primeira < segunda, "histórico em ordem cronológica");
    }

    @Test
    void nomeRepetidoERecusadoEClientePodeTentarOutro() throws IOException {
        conectar("ana");

        ClienteDeTeste intruso = new ClienteDeTeste(servidor.getPortaLocal());
        abertos.add(intruso);
        intruso.lerAte("Informe seu nome");
        intruso.enviar("ANA");
        intruso.lerAte("já está em uso");
        intruso.enviar("bia");
        intruso.lerAte("Conectado como bia");
    }

    // ---- utilidades ----

    private ClienteDeTeste conectar(String nome) throws IOException {
        ClienteDeTeste c = new ClienteDeTeste(servidor.getPortaLocal());
        abertos.add(c);
        c.lerAte("Informe seu nome");
        c.enviar(nome);
        c.lerAte("Conectado como " + nome);
        return c;
    }

    private static int indiceQueTermina(List<String> linhas, String sufixo) {
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).endsWith(sufixo)) {
                return i;
            }
        }
        return -1;
    }

    /** Cliente mínimo por socket, com timeout de leitura para o teste nunca travar. */
    private static final class ClienteDeTeste {
        private final Socket socket;
        private final BufferedReader entrada;
        private final PrintWriter saida;
        private final List<String> lidas = new ArrayList<>();

        ClienteDeTeste(int porta) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), porta);
            socket.setSoTimeout(5000);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void enviar(String linha) {
            saida.println(linha);
        }

        /** Lê linhas até uma conter o trecho; estoura (SocketTimeoutException) se não chegar. */
        void lerAte(String trecho) throws IOException {
            String linha;
            while ((linha = entrada.readLine()) != null) {
                lidas.add(linha);
                if (linha.contains(trecho)) {
                    return;
                }
            }
            throw new IOException("Conexão encerrada antes de receber: " + trecho);
        }

        List<String> linhasLidas() {
            return lidas;
        }

        void fechar() {
            try {
                socket.close();
            } catch (IOException ignorada) {
                // nada a fazer
            }
        }
    }
}
