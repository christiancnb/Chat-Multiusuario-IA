package br.unicesumar.chat.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Não lidas, som, silêncio, status, exportação e restauração de abas da tela do chat (sem janela visível). */
class TelaChatPersonalizacaoTest {

    private TelaChat chat;
    private Preferencias prefs;
    private final AtomicInteger sons = new AtomicInteger();

    @BeforeEach
    void preparar() throws Exception {
        System.setProperty("java.awt.headless", "true");
        prefs = new Preferencias(Preferences.userRoot().node("chat-tela-teste-" + System.nanoTime()));
        chat = naEdt(() -> {
            TelaChat t = new TelaChat();
            t.definirPreferencias(prefs);
            t.definirSomDeAviso(sons::incrementAndGet);
            t.definirSessao("ana", "teste");
            return t;
        });
    }

    @Test
    void mensagemEmAbaOcultaContaComoNaoLidaEToca() throws Exception {
        Conversa bia = Conversa.privada("bia");
        naEdt(() -> { chat.adicionarMensagem(bia, "10:00:00", "bia", "oi"); return null; });
        naEdt(() -> { chat.adicionarMensagem(bia, "10:00:01", "bia", "oi de novo"); return null; });

        assertTrue(naEdt(() -> chat.temNaoLida(bia)));
        assertEquals(2, naEdt(chat::totalNaoLidas));
        assertEquals(2, sons.get());
    }

    @Test
    void abrirAbaZeraAContagem() throws Exception {
        Conversa bia = Conversa.privada("bia");
        naEdt(() -> { chat.adicionarMensagem(bia, "10:00:00", "bia", "oi"); return null; });

        naEdt(() -> { chat.abrirConversa(bia); return null; });

        assertFalse(naEdt(() -> chat.temNaoLida(bia)));
        assertEquals(0, naEdt(chat::totalNaoLidas));
    }

    @Test
    void mensagemPropriaOuNaAbaAtivaNaoContaNemToca() throws Exception {
        naEdt(() -> { chat.adicionarMensagem(Conversa.GERAL, "10:00:00", "bia", "oi geral"); return null; });
        Conversa bia = Conversa.privada("bia");
        naEdt(() -> { chat.adicionarMensagem(bia, "10:00:01", "ana", "eu mesma"); return null; });

        assertEquals(0, naEdt(chat::totalNaoLidas));
        assertEquals(0, sons.get());
    }

    @Test
    void conversaSilenciadaNaoContaNemToca() throws Exception {
        Conversa bia = Conversa.privada("bia");
        naEdt(() -> { chat.abrirConversa(bia); return null; });
        assertTrue(naEdt(chat::alternarSilencioDaAtiva));
        naEdt(() -> { chat.abrirConversa(Conversa.GERAL); return null; });
        sons.set(0);

        naEdt(() -> { chat.adicionarMensagem(bia, "10:00:00", "bia", "psiu"); return null; });

        assertEquals(0, naEdt(chat::totalNaoLidas));
        assertEquals(0, sons.get());
        // reativar volta a notificar
        naEdt(() -> { chat.abrirConversa(bia); chat.alternarSilencioDaAtiva(); chat.abrirConversa(Conversa.GERAL); return null; });
        naEdt(() -> { chat.adicionarMensagem(bia, "10:00:01", "bia", "voltei"); return null; });
        assertEquals(1, naEdt(chat::totalNaoLidas));
        assertEquals(1, sons.get());
    }

    @Test
    void somDesligadoNaPreferenciaNaoToca() throws Exception {
        prefs.definirSom(false);
        Conversa bia = Conversa.privada("bia");

        naEdt(() -> { chat.adicionarMensagem(bia, "10:00:00", "bia", "oi"); return null; });

        assertEquals(0, sons.get());
        assertEquals(1, naEdt(chat::totalNaoLidas), "a contagem continua mesmo sem som");
    }

    @Test
    void avisosDesligadosDuranteARemontagemNaoContamNemTocam() throws Exception {
        naEdt(() -> { chat.definirAvisosLigados(false); return null; });

        naEdt(() -> { chat.adicionarMensagem(Conversa.privada("bia"), "10:00:00", "bia", "oi"); return null; });

        assertEquals(0, sons.get());
        assertEquals(0, naEdt(chat::totalNaoLidas));
    }

    @Test
    void statusAtualizaALegendaEOBotaoDoProprioUsuario() throws Exception {
        naEdt(() -> { chat.atualizarStatus(Map.of("ana", "ocupado", "bia", "ausente")); return null; });

        assertEquals("ocupado", naEdt(() -> chat.statusDe("ANA")));
        assertEquals("ausente", naEdt(() -> chat.statusDe("bia")));
        assertEquals("disponivel", naEdt(() -> chat.statusDe("caio")));

        naEdt(() -> { chat.atualizarStatus(Map.of()); return null; });
        assertEquals("disponivel", naEdt(() -> chat.statusDe("ana")));
    }

    @Test
    void exportaAConversaAtivaEmArquivoUtf8() throws Exception {
        naEdt(() -> { chat.adicionarMensagem(Conversa.GERAL, "10:00:00", "bia", "ação rápida"); return null; });
        Path arquivo = Files.createTempDirectory("tela-export").resolve("geral.txt");

        Path gravado = naEdt(() -> chat.exportarConversaAtiva(arquivo));

        String texto = Files.readString(gravado, StandardCharsets.UTF_8);
        assertTrue(texto.contains("Geral"));
        assertTrue(texto.contains("[10:00:00] bia: ação rápida"));
    }

    @Test
    void exportarSemPermissaoLancaIOException() throws Exception {
        Path pasta = Files.createTempDirectory("tela-export");
        Path ocupado = pasta.resolve("arquivo");
        Files.writeString(ocupado, "x");
        boolean lancou = false;
        try {
            naEdt(() -> chat.exportarConversaAtiva(ocupado.resolve("filho.txt")));
        } catch (IOException e) {
            lancou = true;
        }
        assertTrue(lancou);
    }

    @Test
    void restaurarAbasRecriaAsAbertasEMarcaAAtiva() throws Exception {
        Conversa bia = Conversa.privada("bia");
        Conversa grupo = Conversa.grupo("estudo");

        naEdt(() -> { chat.restaurarAbas(List.of(Conversa.GERAL, bia, grupo), grupo); return null; });

        assertTrue(naEdt(() -> chat.conversasAbertas()).containsAll(List.of(Conversa.GERAL, bia, grupo)));
        assertEquals(grupo, naEdt(chat::conversaAtiva));
    }

    @Test
    void comandoDeResumoDependeDaConversa() {
        assertEquals("/uci resumir", TelaChat.comandoDeResumo(Conversa.GERAL));
        assertEquals("/uci resumir #estudo", TelaChat.comandoDeResumo(Conversa.grupo("Estudo")));
        assertEquals("/uci resumir bia", TelaChat.comandoDeResumo(Conversa.privada("bia")));
        assertEquals("/uci resumir", TelaChat.comandoDeResumo(Conversa.privada("UCI")));
    }

    private static <T> T naEdt(Callable<T> c) throws Exception {
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
}
