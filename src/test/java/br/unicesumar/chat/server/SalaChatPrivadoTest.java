package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mensagens privadas: entrega só ao destinatário, regras de negócio e histórico entre dois usuários. */
@Timeout(30)
class SalaChatPrivadoTest {

    private RepositorioEmMemoria repositorio;
    private SalaChat sala;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEmMemoria();
        sala = new SalaChat(repositorio, 20);
    }

    @Test
    void mensagemPrivadaChegaSoAoDestinatarioESemEcoAoRemetente() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        DestinatarioFalso caio = conectar("caio");

        sala.publicarPrivada(ana, "bia", "segredo");

        assertTrue(bia.recebeuContendo("ana -> bia (privado): segredo"));
        assertFalse(caio.recebeuContendo("segredo"), "terceiros não podem ver");
        assertFalse(ana.recebeuContendo("segredo"), "o remetente não recebe eco do servidor");
    }

    @Test
    void destinatarioEncontradoSemDiferenciarMaiusculasEGravadoComONomeCanonico() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("Bia");

        Mensagem m = sala.publicarPrivada(ana, "BIA", "oi");

        assertTrue(bia.recebeuContendo("ana -> Bia (privado): oi"));
        assertEquals(Mensagem.Tipo.PRIVADA, m.getTipo());
        assertEquals("Bia", m.getDestino());
        assertEquals("ana", m.getRemetente());
        assertEquals(1, repositorio.todas().size());
        assertEquals(Mensagem.Tipo.PRIVADA, repositorio.todas().get(0).getTipo());
    }

    @Test
    void destinatarioOfflineGeraErroENadaEGravado() {
        DestinatarioFalso ana = conectar("ana");

        ChatException e = assertThrows(ChatException.class, () -> sala.publicarPrivada(ana, "fantasma", "oi"));

        assertTrue(e.getMessage().contains("não está online"));
        assertTrue(repositorio.todas().isEmpty());
    }

    @Test
    void naoEPossivelEnviarParaSiMesmo() {
        DestinatarioFalso ana = conectar("ana");

        ChatException e = assertThrows(ChatException.class, () -> sala.publicarPrivada(ana, "ANA", "oi"));

        assertTrue(e.getMessage().contains("si mesmo"));
    }

    @Test
    void conteudoInvalidoNaoEGravadoNemEntregue() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        assertThrows(IllegalArgumentException.class, () -> sala.publicarPrivada(ana, "bia", "   "));
        assertThrows(IllegalArgumentException.class, () -> sala.publicarPrivada(ana, "bia", "x".repeat(501)));

        assertTrue(bia.mensagens().isEmpty());
        assertTrue(repositorio.todas().isEmpty());
    }

    @Test
    void destinatarioQueCaiuEhRemovidoEOsOutrosSaoAvisados() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        DestinatarioFalso caio = conectar("caio");
        bia.simularQueda();

        ChatException e = assertThrows(ChatException.class, () -> sala.publicarPrivada(ana, "bia", "alô?"));

        assertTrue(e.getMessage().contains("não está mais online"));
        assertEquals(2, sala.totalConectados());
        assertTrue(caio.recebidas().contains("* bia saiu do chat."));
    }

    @Test
    void semUciAtivaConversarComElaGeraErroClaro() {
        DestinatarioFalso ana = conectar("ana");

        ChatException e = assertThrows(ChatException.class, () -> sala.publicarPrivada(ana, "UCI", "oi"));

        assertTrue(e.getMessage().contains("não está disponível"));
    }

    @Test
    void falhaNoBancoNaoImpedeAEntregaPrivada() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        repositorio.falharSempre();

        sala.publicarPrivada(ana, "bia", "sem banco");

        assertTrue(bia.recebeuContendo("sem banco"));
    }

    @Test
    void mensagemPrivadaNaoApareceNoHistoricoGeralDeQuemEntraDepois() {
        DestinatarioFalso ana = conectar("ana");
        conectar("bia");
        sala.publicarPrivada(ana, "bia", "só nós dois");

        DestinatarioFalso caio = conectar("caio");

        assertFalse(caio.recebeuContendo("só nós dois"));
        assertEquals("--- Nenhuma mensagem anterior ---", caio.recebidas().get(0));
    }

    @Test
    void historicoPrivadoTrazSoAConversaDosDoisEmOrdem() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        DestinatarioFalso caio = conectar("caio");
        sala.publicarPrivada(ana, "bia", "um");
        sala.publicarPrivada(bia, "ana", "dois");
        sala.publicarPrivada(ana, "caio", "outra conversa");
        ana.recebidas().clear();

        sala.enviarHistoricoPrivado(ana, "BIA");

        assertEquals("--- privado com BIA: últimas 2 mensagens ---", ana.recebidas().get(0));
        assertTrue(ana.recebidas().get(1).endsWith("ana -> bia (privado): um"));
        assertTrue(ana.recebidas().get(2).endsWith("bia -> ana (privado): dois"));
        assertEquals("--- privado com BIA: fim do histórico ---", ana.recebidas().get(3));
        assertEquals(4, ana.recebidas().size(), "a conversa com o caio não entra");
        assertTrue(caio.recebeuContendo("outra conversa"));
    }

    @Test
    void historicoPrivadoSemMensagensInformaQueEstaVazio() {
        DestinatarioFalso ana = conectar("ana");
        conectar("bia");

        sala.enviarHistoricoPrivado(ana, "bia");

        assertTrue(ana.recebidas().contains("--- privado com bia: nenhuma mensagem anterior ---"));
    }

    @Test
    void historicoPrivadoComNomeInvalidoELancado() {
        DestinatarioFalso ana = conectar("ana");
        assertThrows(IllegalArgumentException.class, () -> sala.enviarHistoricoPrivado(ana, "nome com espaço"));
    }

    private DestinatarioFalso conectar(String nome) {
        DestinatarioFalso d = new DestinatarioFalso(nome);
        assertTrue(sala.entrar(d));
        return d;
    }
}
