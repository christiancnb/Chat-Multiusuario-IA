package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrupoTest {

    @Test
    void nomeEhNormalizadoParaMinusculasESemCerquilha() {
        assertEquals("estudo", new Grupo("#Estudo", "ana").getNome());
        assertEquals("time-2026", new Grupo("  Time-2026 ", "ana").getNome());
    }

    @Test
    void rejeitaNomeInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Grupo(null, "ana"));
        assertThrows(IllegalArgumentException.class, () -> new Grupo("   ", "ana"));
        assertThrows(IllegalArgumentException.class, () -> new Grupo("a", "ana"));
        assertThrows(IllegalArgumentException.class, () -> new Grupo("a".repeat(21), "ana"));
        assertThrows(IllegalArgumentException.class, () -> new Grupo("meu grupo", "ana"));
        assertThrows(IllegalArgumentException.class, () -> new Grupo("grupo:1", "ana"));
    }

    @Test
    void criadorEhObrigatorio() {
        assertThrows(IllegalArgumentException.class, () -> new Grupo("estudo", null));
        assertThrows(IllegalArgumentException.class, () -> new Grupo("estudo", "  "));
    }

    @Test
    void membrosSaoComparadosSemDiferenciarMaiusculas() {
        Grupo g = new Grupo("estudo", "ana");
        assertTrue(g.adicionar("Ana"));
        assertFalse(g.adicionar("ana"), "segunda adição do mesmo usuário não tem efeito");
        assertTrue(g.contem("ANA"));
        assertFalse(g.contem("bia"));
        assertEquals(1, g.getMembros().size());
    }

    @Test
    void removerDevolveSeEraMembro() {
        Grupo g = new Grupo("estudo", "ana");
        g.adicionar("ana");
        assertTrue(g.remover("Ana"));
        assertFalse(g.remover("ana"));
        assertFalse(g.contem("ana"));
    }

    @Test
    void listaDeMembrosEhCopiaImutavel() {
        Grupo g = new Grupo("estudo", "ana");
        g.adicionar("ana");
        assertThrows(UnsupportedOperationException.class, () -> g.getMembros().add("intruso"));
        assertEquals(1, g.getMembros().size());
    }

    @Test
    void copiarGeraGrupoIndependente() {
        Grupo original = new Grupo("estudo", "ana");
        original.adicionar("ana");

        Grupo copia = original.copiar();
        copia.adicionar("bia");
        original.remover("ana");

        assertEquals("estudo", copia.getNome());
        assertEquals("ana", copia.getCriador());
        assertTrue(copia.contem("ana"));
        assertTrue(copia.contem("bia"));
        assertFalse(original.contem("bia"));
    }
}
