package br.unicesumar.chat.client;

import br.unicesumar.chat.agente.AgenteLocal;
import br.unicesumar.chat.agente.ServicoUci;
import br.unicesumar.chat.persistence.GrupoRepositoryMemoria;
import br.unicesumar.chat.server.RepositorioEmMemoria;
import br.unicesumar.chat.server.SalaChat;
import br.unicesumar.chat.server.Servidor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A interface gráfica de ponta a ponta (sem janela visível): AplicacaoChat de verdade falando
 * com um Servidor de verdade por socket, com a UCI em modo offline (AgenteLocal).
 */
@Timeout(60)
class AplicacaoChatTest {

    private Servidor servidor;
    private ServicoUci uci;
    private AplicacaoChat app;
    private final List<ConexaoChat> abertas = new ArrayList<>();

    @BeforeEach
    void preparar() throws Exception {
        System.setProperty("java.awt.headless", "true");
        uci = new ServicoUci(new AgenteLocal());
        servidor = new Servidor(0, new SalaChat(new RepositorioEmMemoria(), new GrupoRepositoryMemoria(), uci, 20));
        servidor.abrir();
        Thread t = new Thread(servidor::aceitarConexoes, "aceitador-gui-teste");
        t.setDaemon(true);
        t.start();
        app = naEdt(() -> new AplicacaoChat(isolada()));
    }

    @AfterEach
    void derrubar() {
        abertas.forEach(ConexaoChat::desconectar);
        if (app != null) {
            app.encerrar();
        }
        uci.close();
        servidor.parar();
    }

    @Test
    void comecaNaTelaDeInicioSemConversaEmAndamento() throws Exception {
        assertFalse(app.conectado());
        assertFalse(naEdt(() -> app.telaInicio().botaoVoltar().isVisible()));
    }

    @Test
    void entrarAbreOChatGeralEMostraUsuariosOnline() throws Exception {
        entrarComo("ana");

        assertTrue(app.conectado());
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains("ana"))));
        assertEquals(Conversa.GERAL, naEdt(() -> app.telaChat().conversaAtiva()));
    }

    @Test
    void mensagemDoGeralApareceNaAbaGeralDeQuemRecebe() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");

        bia.conexao.enviar("olá, ana!");

        assertTrue(esperar(() -> textoGeral().contains("bia") && textoGeral().contains("olá, ana!")));
    }

    @Test
    void conversaPrivadaAbreAbaPropriaENaoApareceNoGeral() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains("bia"))));

        bia.conexao.enviar("/msg ana segredo");

        Conversa comBia = Conversa.privada("bia");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().conversasAbertas().contains(comBia))));
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().textoDa(comBia)).contains("segredo")));
        assertFalse(textoGeral().contains("segredo"));
        // ana responde dentro da aba privada
        naEdt(() -> { app.telaChat().abrirConversa(comBia); return null; });
        enviarPelaTela("de volta");
        assertTrue(bia.esperar("ana -> bia (privado): de volta"));
    }

    @Test
    void mensagemPrivadaParaQuemEstaOfflineEhRecusadaLocalmente() throws Exception {
        entrarComo("ana");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains("ana"))));
        Conversa fantasma = Conversa.privada("fantasma");
        naEdt(() -> { app.telaChat().abrirConversa(fantasma); return null; });

        enviarPelaTela("alguém aí?");

        assertTrue(esperar(() -> naEdt(() -> app.telaChat().textoDa(fantasma)).contains("não está online")));
    }

    @Test
    void criarGrupoPeloComandoMostraNaListaEAceitaMensagens() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");

        enviarPelaTela("/grupo criar estudo");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().meusGrupos().contains("estudo"))));

        bia.conexao.enviar("/grupo entrar estudo");
        bia.conexao.enviar("/g estudo oi grupo");
        Conversa grupo = Conversa.grupo("estudo");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().textoDa(grupo)).contains("oi grupo")));

        enviarPelaTela("/grupo sair estudo");
        assertTrue(esperar(() -> naEdt(() -> !app.telaChat().meusGrupos().contains("estudo"))));
    }

    @Test
    void perguntarParaUciNoPrivadoMostraDigitandoERespostaDela() throws Exception {
        entrarComo("ana");
        Conversa comUci = Conversa.privada("UCI");
        naEdt(() -> { app.telaChat().abrirConversa(comUci); return null; });

        enviarPelaTela("oi");

        assertTrue(esperar(() -> naEdt(() -> app.telaChat().textoDa(comUci)).contains("UCI")),
                "a resposta da UCI aparece na aba dela");
        assertTrue(esperar(() -> !naEdt(() -> app.telaChat().textoDoStatus()).contains("digitando")),
                "o aviso 'digitando' some quando ela responde");
    }

    @Test
    void mencionarUciNoGeralTodosVeemARespostaPublica() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");

        enviarPelaTela("@UCI oi");

        assertTrue(bia.esperar("UCI"));
        assertTrue(bia.linhas.stream().noneMatch(l -> l.startsWith("@@")), "console nunca recebe linhas de controle");
    }

    @Test
    void inicioVoltarESairFuncionam() throws Exception {
        entrarComo("ana");

        naEdt(() -> { app.telaChat().botaoInicio().doClick(); return null; });
        assertTrue(naEdt(() -> app.telaInicio().botaoVoltar().isVisible()));
        naEdt(() -> { app.telaInicio().botaoVoltar().doClick(); return null; });
        assertTrue(app.conectado());

        naEdt(() -> { app.telaChat().botaoSair().doClick(); return null; });
        assertTrue(esperar(() -> !app.conectado()));
    }

    // ---------------------------------------------------------------- utilidades

    private void entrarComo(String nome) throws Exception {
        naEdt(() -> {
            app.telaInicio().definirDados("localhost", String.valueOf(servidor.getPortaLocal()), nome);
            app.telaInicio().botaoEntrar().doClick();
            return null;
        });
        assertTrue(esperar(app::conectado), "deveria conectar");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains(nome))));
    }

    private void enviarPelaTela(String texto) throws Exception {
        naEdt(() -> {
            app.telaChat().campoMensagem().setText(texto);
            app.telaChat().botaoEnviar().doClick();
            return null;
        });
    }

    private String textoGeral() throws Exception {
        return naEdt(() -> app.telaChat().textoDa(Conversa.GERAL));
    }

    private Registro conectarConsole(String nome) throws Exception {
        Registro r = new Registro();
        r.conexao = new ConexaoChat(r);
        r.conexao.conectar("localhost", servidor.getPortaLocal(), nome);
        abertas.add(r.conexao);
        return r;
    }

    private static Preferences isolada() {
        return Preferences.userRoot().node("chat-teste-" + System.nanoTime());
    }

    private static <T> T naEdt(java.util.concurrent.Callable<T> c) throws Exception {
        AtomicReference<T> r = new AtomicReference<>();
        AtomicReference<Exception> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                r.set(c.call());
            } catch (Exception e) {
                erro.set(e);
            }
        });
        if (erro.get() != null) {
            throw erro.get();
        }
        return r.get();
    }

    private static boolean esperar(BooleanSupplierEx c) throws Exception {
        long limite = System.nanoTime() + 8_000_000_000L;
        while (System.nanoTime() < limite) {
            if (c.get()) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    @FunctionalInterface
    private interface BooleanSupplierEx {
        boolean get() throws Exception;
    }

    private static final class Registro implements ConexaoChat.Ouvinte {
        final List<String> linhas = new CopyOnWriteArrayList<>();
        ConexaoChat conexao;

        @Override
        public void aoReceber(String linha) {
            linhas.add(linha);
        }

        @Override
        public void aoDesconectar(boolean pedidoPeloUsuario) {
        }

        boolean esperar(String trecho) throws Exception {
            return AplicacaoChatTest.esperar(() -> linhas.stream().anyMatch(l -> l.contains(trecho)));
        }
    }
}
