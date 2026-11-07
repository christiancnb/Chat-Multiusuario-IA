package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConversaTest {

    @Test
    void textoNoChatGeralVaiComoEsta() {
        assertEquals("olá, pessoal", Conversa.GERAL.comandoDeEnvio("olá, pessoal"));
    }

    @Test
    void textoNoGrupoViraComandoG() {
        assertEquals("/g estudo prova amanhã", Conversa.grupo("estudo").comandoDeEnvio("prova amanhã"));
    }

    @Test
    void textoNaConversaPrivadaViraComandoMsgComONomeComoExibido() {
        assertEquals("/msg Bia oi", Conversa.privada("Bia").comandoDeEnvio("oi"));
    }

    @Test
    void comandosSeguemComoEstaemQualquerAba() {
        assertEquals("/usuarios", Conversa.grupo("estudo").comandoDeEnvio("/usuarios"));
        assertEquals("/grupo sair estudo", Conversa.privada("bia").comandoDeEnvio("/grupo sair estudo"));
    }

    @Test
    void grupoAceitaHashEMaiusculasENormaliza() {
        Conversa c = Conversa.grupo("  #Estudo ");
        assertEquals("estudo", c.chave());
        assertEquals("#estudo", c.titulo());
        assertEquals(Conversa.Tipo.GRUPO, c.tipo());
    }

    @Test
    void conversasSaoIguaisPeloTipoEPelaChaveSemDiferenciarMaiusculas() {
        assertEquals(Conversa.privada("Ana"), Conversa.privada("ana"));
        assertEquals(Conversa.grupo("#x1"), Conversa.grupo("X1"));
        assertEquals(Conversa.privada("Ana").hashCode(), Conversa.privada("ANA").hashCode());
    }

    @Test
    void privadaEGrupoComOMesmoNomeSaoConversasDiferentes() {
        assertFalse(Conversa.privada("estudo").equals(Conversa.grupo("estudo")));
    }

    @Test
    void privadaGuardaONomeComoFoiInformadoParaExibicao() {
        assertEquals("Bia", Conversa.privada(" Bia ").titulo());
        assertEquals("bia", Conversa.privada("Bia").chave());
    }
}
