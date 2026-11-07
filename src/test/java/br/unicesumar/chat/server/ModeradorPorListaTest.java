package br.unicesumar.chat.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeradorPorListaTest {

    private final ModeradorDeMensagens moderador = new ModeradorPorLista(List.of("burro", "otario"));

    @Test
    void trocaSoPalavrasInteirasSemDiferenciarCaixaOuAcento() {
        assertEquals("seu ***** e ****** tambem", moderador.moderar("seu BURRO e Otário tambem"));
        assertEquals("burrinho e aburrado", moderador.moderar("burrinho e aburrado"), "só palavra inteira");
    }

    @Test
    void textoLimpoFicaIgualEPreservaAcentos() {
        assertEquals("Olá, tudo bem? Ação!", moderador.moderar("Olá, tudo bem? Ação!"));
    }

    @Test
    void mantemOTamanhoDoTextoOriginal() {
        String original = "Não seja burro, otário!";
        assertEquals(original.length(), moderador.moderar(original).length());
    }

    @Test
    void listaPadraoExiste() {
        assertEquals("******", ModeradorPorLista.padrao().moderar("idiota"));
    }

    @Test
    void moderadorNenhumNaoAltera() {
        assertEquals("idiota", ModeradorDeMensagens.NENHUM.moderar("idiota"));
    }
}
