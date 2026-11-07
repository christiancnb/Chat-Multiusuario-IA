package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioTest {

    @Test
    void aceitaNomeValidoERemoveEspacosDasPontas() {
        assertEquals("joao_silva", new Usuario("  joao_silva ").getNome());
    }

    @Test
    void aceitaLetrasAcentuadas() {
        assertEquals("José", new Usuario("José").getNome());
    }

    @Test
    void rejeitaNomeNuloVazioCurtoLongoOuComEspaco() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(null));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("   "));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("a"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("a".repeat(21)));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("maria clara"));
    }

    @Test
    void comparaNomesIgnorandoMaiusculas() {
        assertEquals(new Usuario("Ana"), new Usuario("ana"));
        assertEquals(new Usuario("Ana").hashCode(), new Usuario("ANA").hashCode());
    }

    @Test
    void nomeDaAssistenteEReservadoSemDiferenciarMaiusculas() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("UCI"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("uci"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("  Uci "));
        assertTrue(Usuario.reservado("uCi"));
        assertFalse(Usuario.reservado("ucia"));
        assertFalse(Usuario.nomeValido("UCI"));
    }

    @Test
    void chaveEMinusculaESemEspacosNasPontas() {
        assertEquals("ana", Usuario.chave("  ANA "));
        assertEquals("", Usuario.chave(null));
    }

    @Test
    void nomeValidoNaoLancaExcecao() {
        assertTrue(Usuario.nomeValido("ana"));
        assertFalse(Usuario.nomeValido("a b"));
        assertFalse(Usuario.nomeValido(null));
    }
}
