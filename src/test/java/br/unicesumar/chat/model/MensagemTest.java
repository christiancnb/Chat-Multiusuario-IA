package br.unicesumar.chat.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MensagemTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 8, 21, 3, 15);

    @Test
    void formataComHorarioRemetenteEConteudo() {
        Mensagem m = new Mensagem("ana", "olá, pessoal!", HORA);
        assertEquals("[21:03:15] ana: olá, pessoal!", m.formatar());
    }

    @Test
    void removeEspacosNasPontasDoConteudo() {
        Mensagem m = new Mensagem("ana", "   oi   ", HORA);
        assertEquals("oi", m.getConteudo());
    }

    @Test
    void rejeitaConteudoVazioOuEmBranco() {
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("ana", "", HORA));
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("ana", "    ", HORA));
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("ana", null, HORA));
    }

    @Test
    void rejeitaRemetenteVazio() {
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("", "oi", HORA));
        assertThrows(IllegalArgumentException.class, () -> new Mensagem(null, "oi", HORA));
    }

    @Test
    void aceitaExatamenteOLimiteERejeitaAcima() {
        String limite = "a".repeat(Mensagem.TAMANHO_MAXIMO);
        assertEquals(Mensagem.TAMANHO_MAXIMO, new Mensagem("ana", limite, HORA).getConteudo().length());
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("ana", limite + "a", HORA));
    }

    @Test
    void rejeitaQuebraDeLinhaPorqueCadaMensagemEUmaLinhaDoProtocolo() {
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("ana", "linha1\nlinha2", HORA));
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("ana", "linha1\rlinha2", HORA));
    }

    @Test
    void mensagemGeralNaoTemDestino() {
        Mensagem m = new Mensagem("ana", "oi", HORA);
        assertEquals(Mensagem.Tipo.GERAL, m.getTipo());
        assertNull(m.getDestino());
    }

    @Test
    void mensagemDeGrupoEhFormatadaComOGrupo() {
        Mensagem m = Mensagem.grupo("estudo", "ana", "olá, grupo!", HORA);
        assertEquals(Mensagem.Tipo.GRUPO, m.getTipo());
        assertEquals("estudo", m.getDestino());
        assertEquals("[21:03:15] #estudo ana: olá, grupo!", m.formatar());
    }

    @Test
    void mensagemPrivadaEhFormatadaComRemetenteEDestinatario() {
        Mensagem m = Mensagem.privada("bia", "ana", "oi, bia", HORA);
        assertEquals(Mensagem.Tipo.PRIVADA, m.getTipo());
        assertEquals("bia", m.getDestino());
        assertEquals("[21:03:15] ana -> bia (privado): oi, bia", m.formatar());
    }

    @Test
    void grupoEPrivadaExigemDestino() {
        assertThrows(IllegalArgumentException.class, () -> Mensagem.grupo(null, "ana", "oi", HORA));
        assertThrows(IllegalArgumentException.class, () -> Mensagem.grupo("  ", "ana", "oi", HORA));
        assertThrows(IllegalArgumentException.class, () -> Mensagem.privada("", "ana", "oi", HORA));
    }

    @Test
    void mensagensDeGrupoEPrivadasTambemValidamOConteudo() {
        assertThrows(IllegalArgumentException.class, () -> Mensagem.grupo("estudo", "ana", "  ", HORA));
        assertThrows(IllegalArgumentException.class,
                () -> Mensagem.privada("bia", "ana", "a".repeat(Mensagem.TAMANHO_MAXIMO + 1), HORA));
        assertThrows(IllegalArgumentException.class, () -> Mensagem.privada("bia", "ana", "a\nb", HORA));
    }

    @Test
    void comIdPreservaTipoEDestino() {
        Mensagem salva = Mensagem.grupo("estudo", "ana", "oi", HORA).comId(7L);
        assertEquals(7L, salva.getId());
        assertEquals(Mensagem.Tipo.GRUPO, salva.getTipo());
        assertEquals("estudo", salva.getDestino());
    }

    @Test
    void comIdDevolveCopiaComIdSemAlterarAOriginal() {
        Mensagem original = new Mensagem("ana", "oi", HORA);
        Mensagem salva = original.comId(42L);
        assertNull(original.getId());
        assertEquals(42L, salva.getId());
        assertEquals(original.formatar(), salva.formatar());
    }
}
