package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusUsuarioTest {

    @Test
    void aceitaComOuSemAcentoEQualquerCaixa() {
        assertEquals(Optional.of(StatusUsuario.DISPONIVEL), StatusUsuario.de("Disponível"));
        assertEquals(Optional.of(StatusUsuario.OCUPADO), StatusUsuario.de("  OCUPADO "));
        assertEquals(Optional.of(StatusUsuario.AUSENTE), StatusUsuario.de("ausente"));
    }

    @Test
    void valoresInvalidosViramVazio() {
        assertEquals(Optional.empty(), StatusUsuario.de("dormindo"));
        assertEquals(Optional.empty(), StatusUsuario.de(null));
        assertEquals(Optional.empty(), StatusUsuario.de(""));
    }
}
