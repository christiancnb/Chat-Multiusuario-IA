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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Os recursos de personalização de ponta a ponta: janela de verdade, servidor de verdade, sockets de verdade. */
@Timeout(90)
class AplicacaoChatPersonalizacaoTest {

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
        Thread t = new Thread(servidor::aceitarConexoes, "aceitador-personalizacao");
        t.setDaemon(true);
        t.start();
        app = naEdt(() -> new AplicacaoChat(Preferences.userRoot().node("chat-pers-" + System.nanoTime())));
    }

    @AfterEach
    void derrubar() {
        Tema.aplicar(false, 1f, false);
        Tema.definirIdentidade("", null);
        abertas.forEach(ConexaoChat::desconectar);
        app.encerrar();
        uci.close();
        servidor.parar();
    }

    @Test
    void statusDeOutroUsuarioApareceNaListaEMeuStatusPeloComando() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains("bia"))));

        bia.conexao.enviar("/status ocupado");
        assertTrue(esperar(() -> "ocupado".equals(naEdt(() -> app.telaChat().statusDe("bia")))));

        enviarPelaTela("/status ausente");
        assertTrue(esperar(() -> "ausente".equals(naEdt(() -> app.telaChat().statusDe("ana")))));

        bia.conexao.enviar("/usuarios");
        assertTrue(bia.esperar("ana (ausente)"), "o terminal vê o status no /usuarios");
    }

    @Test
    void digitandoDeOutroApareceSoNaConversaCerta() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains("bia"))));

        bia.conexao.enviar("/digitando geral");

        assertTrue(esperar(() -> naEdt(() -> app.telaChat().textoDoStatus()).contains("bia está digitando")));
        // chegou a mensagem: o aviso some
        bia.conexao.enviar("pronto");
        assertTrue(esperar(() -> !naEdt(() -> app.telaChat().textoDoStatus()).contains("digitando")));
    }

    @Test
    void digitandoEmOutraConversaNaoApareceNaAbaAtiva() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains("bia"))));

        bia.conexao.enviar("/digitando ana"); // privado: a conversa de ana com bia, que não está aberta
        bia.conexao.enviar("/digitando geral");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().textoDoStatus()).contains("bia está digitando")));
        // se o privado tivesse aparecido, a aba ativa (Geral) não deveria ter mudado
        assertEquals(Conversa.GERAL, naEdt(() -> app.telaChat().conversaAtiva()));
    }

    @Test
    void avisoDeDigitacaoEhEnviadoNoMaximoACadaTresSegundos() throws Exception {
        AtomicLong agora = new AtomicLong(1_000_000_000_000L);
        naEdt(() -> { app.relogioNs = agora::get; return null; });
        entrarComo("ana");
        Registro bia = conectarConsole("bia");
        bia.conexao.enviar(br.unicesumar.chat.protocolo.Protocolo.MODO_GUI);
        assertTrue(bia.esperar("@@USUARIOS|"), "o servidor confirma o modo gráfico enviando o estado");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains("bia"))));

        digitar("a");
        digitar("ab");
        digitar("abc");
        assertTrue(bia.esperar("@@DIGITANDO|ana|geral"));
        assertEquals(1, bia.contar("@@DIGITANDO|ana|geral"), "três teclas seguidas geram um só aviso");

        agora.addAndGet(4_000_000_000L);
        digitar("abcd");
        assertTrue(esperar(() -> bia.contar("@@DIGITANDO|ana|geral") == 2));
    }

    @Test
    void comandoComBarraNaoContaComoDigitando() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");
        bia.conexao.enviar(br.unicesumar.chat.protocolo.Protocolo.MODO_GUI);
        assertTrue(bia.esperar("@@USUARIOS|"), "o servidor confirma o modo gráfico enviando o estado");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().online().contains("bia"))));

        digitar("/ajuda");
        bia.conexao.enviar("/usuarios"); // marca de fim: respondida depois de qualquer aviso anterior
        assertTrue(bia.esperar("Conectados"));
        assertEquals(0, bia.contar("@@DIGITANDO"));
    }

    @Test
    void buscarMostraResultadosNaAbaAtiva() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");
        bia.conexao.enviar("encontre a palavra marciano nesta frase");
        assertTrue(esperar(() -> textoGeral().contains("marciano")));

        enviarPelaTela("/buscar MARCIANO");

        assertTrue(esperar(() -> textoGeral().contains("Busca por \"MARCIANO\": 1 resultado")));
    }

    @Test
    void erroDeUsoApareceNaTelaComOMotivo() throws Exception {
        entrarComo("ana");

        enviarPelaTela("/uci traduzir");

        assertTrue(esperar(() -> textoGeral().contains("Uso: /uci traduzir <idioma> <texto>")));
    }

    @Test
    void trocarOTemaRemontaAJanelaSemPerderConversaNemAbas() throws Exception {
        entrarComo("ana");
        Registro bia = conectarConsole("bia");
        bia.conexao.enviar("mensagem antes do tema");
        bia.conexao.enviar("/msg ana segredo antes do tema");
        Conversa comBia = Conversa.privada("bia");
        assertTrue(esperar(() -> textoGeral().contains("mensagem antes do tema")));
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().textoDa(comBia)).contains("segredo")));
        enviarPelaTela("minha própria mensagem");
        assertTrue(esperar(() -> textoGeral().contains("minha própria mensagem")));
        naEdt(() -> { app.telaChat().abrirConversa(comBia); return null; });

        naEdt(() -> {
            app.preferenciasDoUsuario().definirEscuro(true);
            app.preferenciasDoUsuario().definirFonte(Preferencias.TamanhoFonte.GRANDE);
            app.preferenciasDoUsuario().definirCompacto(true);
            app.preferenciasDoUsuario().definirCor(3);
            app.reaplicarAparencia();
            return null;
        });

        assertTrue(Tema.escuro());
        assertTrue(Tema.compacto());
        assertEquals(1.15f, Tema.escala(), 0.001f);
        assertTrue(app.conectado());
        assertTrue(textoGeral().contains("mensagem antes do tema"));
        assertTrue(textoGeral().contains("minha própria mensagem"), "o que eu enviei também volta");
        assertTrue(naEdt(() -> app.telaChat().textoDa(comBia)).contains("segredo antes do tema"));
        assertEquals(comBia, naEdt(() -> app.telaChat().conversaAtiva()), "a aba ativa é mantida");
        assertTrue(naEdt(() -> app.telaChat().online().contains("bia")), "a lista de online volta");
        assertFalse(naEdt(() -> app.telaChat().temNaoLida(comBia)), "remontar não gera não lidas falsas");

        // a conversa continua funcionando depois da remontagem
        bia.conexao.enviar("/msg ana depois do tema");
        assertTrue(esperar(() -> naEdt(() -> app.telaChat().textoDa(comBia)).contains("depois do tema")));
        enviarPelaTela("resposta depois do tema");
        assertTrue(bia.esperar("ana -> bia (privado): resposta depois do tema"));
    }

    @Test
    void trocarOTemaSemEstarConectadoMantemOsCamposDaTelaInicial() throws Exception {
        naEdt(() -> {
            app.telaInicio().definirDados("meuservidor", "6000", "carla");
            app.preferenciasDoUsuario().definirEscuro(true);
            app.reaplicarAparencia();
            return null;
        });

        assertEquals("meuservidor", naEdt(() -> app.telaInicio().servidor()));
        assertEquals("6000", naEdt(() -> app.telaInicio().porta()));
        assertEquals("carla", naEdt(() -> app.telaInicio().nome()));
        assertFalse(app.conectado());
    }

    @Test
    void modoDaUciEhLembradoEReenviadoAoRemontar() throws Exception {
        entrarComo("ana");

        enviarPelaTela("/uci modo formal");
        assertTrue(esperar(() -> textoGeral().contains("A UCI agora responde no modo formal")));
        assertEquals("formal", app.preferenciasDoUsuario().modoUci());

        enviarPelaTela("/uci modo pirata");
        assertTrue(esperar(() -> textoGeral().contains("Modo desconhecido 'pirata'")));
        assertEquals("formal", app.preferenciasDoUsuario().modoUci(), "modo inválido não é lembrado");
    }

    @Test
    void remontarNaoReenviaOModoDaUciQueJaEstaNoServidor() throws Exception {
        entrarComo("ana");
        enviarPelaTela("/uci modo formal");
        assertTrue(esperar(() -> textoGeral().contains("A UCI agora responde no modo formal")));

        naEdt(() -> { app.preferenciasDoUsuario().definirEscuro(true); app.reaplicarAparencia(); return null; });
        // O servidor atende os comandos de ana em ordem: quando a resposta deste comando chegar,
        // qualquer resposta indevida de um "/uci modo" reenviado antes dele já teria chegado.
        enviarPelaTela("/status ocupado");
        assertTrue(esperar(() -> textoGeral().contains("Status alterado para ocupado.")));

        assertEquals(1, contarOcorrencias(textoGeral(), "A UCI agora responde no modo"),
                "só a confirmação original, sem repetir ao trocar o tema");
    }

    private static int contarOcorrencias(String texto, String trecho) {
        int n = 0;
        for (int i = texto.indexOf(trecho); i >= 0; i = texto.indexOf(trecho, i + 1)) {
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- utilidades

    private void digitar(String texto) throws Exception {
        naEdt(() -> { app.telaChat().campoMensagem().setText(texto); return null; });
    }

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
            return AplicacaoChatPersonalizacaoTest.esperar(() -> linhas.stream().anyMatch(l -> l.contains(trecho)));
        }

        int contar(String trecho) {
            return (int) linhas.stream().filter(l -> l.contains(trecho)).count();
        }
    }

}
