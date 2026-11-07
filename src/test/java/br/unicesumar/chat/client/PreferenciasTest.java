package br.unicesumar.chat.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferenciasTest {

    private Preferences no;
    private Preferencias prefs;

    @BeforeEach
    void preparar() throws Exception {
        no = Preferences.userRoot().node("teste-chat-prefs-" + System.nanoTime());
        prefs = new Preferencias(no);
    }

    @Test
    void padroes() {
        assertFalse(prefs.escuro());
        assertEquals(Preferencias.TamanhoFonte.NORMAL, prefs.fonte());
        assertFalse(prefs.compacto());
        assertTrue(prefs.som());
        assertNull(prefs.cor());
        assertEquals("padrao", prefs.modoUci());
        assertTrue(prefs.silenciadas().isEmpty());
    }

    @Test
    void valoresGravadosSaoLidosDeNovo() {
        prefs.definirEscuro(true);
        prefs.definirFonte(Preferencias.TamanhoFonte.GRANDE);
        prefs.definirCompacto(true);
        prefs.definirSom(false);
        prefs.definirCor(2);
        prefs.definirModoUci("formal");

        Preferencias outra = new Preferencias(no);
        assertTrue(outra.escuro());
        assertEquals(Preferencias.TamanhoFonte.GRANDE, outra.fonte());
        assertTrue(outra.compacto());
        assertFalse(outra.som());
        assertEquals(Preferencias.CORES[2], outra.cor());
        assertEquals("formal", outra.modoUci());
    }

    @Test
    void valoresInvalidosCaemNoPadrao() {
        no.put("fonte", "GIGANTE");
        no.put("cor", "99");
        assertEquals(Preferencias.TamanhoFonte.NORMAL, prefs.fonte());
        assertNull(prefs.cor());
        no.put("cor", "abc");
        assertEquals(-1, prefs.indiceDaCor());
        prefs.definirCor(-5);
        assertEquals(-1, prefs.indiceDaCor());
    }

    @Test
    void silenciarAlternaPorConversa() {
        Conversa grupo = Conversa.grupo("Estudo");

        assertTrue(prefs.alternarSilencio(grupo));
        assertTrue(prefs.estaSilenciada(grupo));
        assertTrue(prefs.estaSilenciada(Conversa.grupo("estudo")), "maiúsculas não importam");
        assertFalse(prefs.estaSilenciada(Conversa.privada("bia")));

        assertFalse(prefs.alternarSilencio(grupo));
        assertFalse(prefs.estaSilenciada(grupo));
    }

    @Test
    void aplicarAoTemaMudaAPaletaEAEscala() {
        try {
            prefs.definirEscuro(true);
            prefs.definirFonte(Preferencias.TamanhoFonte.PEQUENA);
            prefs.definirCompacto(true);
            prefs.definirCor(1);

            prefs.aplicarAoTema("ana");

            assertTrue(Tema.escuro());
            assertTrue(Tema.compacto());
            assertEquals(0.9f, Tema.escala(), 0.001f);
            assertEquals(new Color(0x0B1220), Tema.FUNDO_APP);
            assertEquals(Preferencias.CORES[1], Tema.corDaPessoa("ANA"));
            assertEquals(Tema.corDaPessoa("bia"), Tema.corDaPessoa("bia"));
        } finally {
            Tema.aplicar(false, 1f, false);
            Tema.definirIdentidade("", null);
        }
        assertEquals(new Color(0xF1F5F9), Tema.FUNDO_APP);
    }

    @Test
    void escalaDeFonteTemLimites() {
        try {
            Tema.aplicar(false, 9f, false);
            assertEquals(1.6f, Tema.escala(), 0.001f);
            Tema.aplicar(false, 0.1f, false);
            assertEquals(0.8f, Tema.escala(), 0.001f);
        } finally {
            Tema.aplicar(false, 1f, false);
        }
    }

    @Test
    void fonteSegueAEscala() {
        try {
            Tema.aplicar(false, 1f, false);
            float normal = Tema.fonte(java.awt.Font.PLAIN, 10f).getSize2D();
            Tema.aplicar(false, 1.3f, false);
            assertEquals(normal * 1.3f, Tema.fonte(java.awt.Font.PLAIN, 10f).getSize2D(), 0.01f);
        } finally {
            Tema.aplicar(false, 1f, false);
        }
    }
}
