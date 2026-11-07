package br.unicesumar.chat.protocolo;

import br.unicesumar.chat.model.Mensagem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O {@link Interpretador} é a "tradução" das linhas do servidor; estes testes fixam o contrato do protocolo. */
class InterpretadorTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 8, 21, 3, 15);

    @Test
    void mensagemGeral() {
        assertEquals(new Evento.MensagemGeral("21:03:15", "ana", "olá, pessoal"),
                Interpretador.interpretar("[21:03:15] ana: olá, pessoal"));
    }

    @Test
    void mensagemDeGrupo() {
        assertEquals(new Evento.MensagemGrupo("21:03:15", "estudo", "ana", "bora estudar"),
                Interpretador.interpretar("[21:03:15] #estudo ana: bora estudar"));
    }

    @Test
    void mensagemPrivada() {
        assertEquals(new Evento.MensagemPrivada("21:03:15", "ana", "bia", "oi"),
                Interpretador.interpretar("[21:03:15] ana -> bia (privado): oi"));
    }

    @Test
    void avisos() {
        assertEquals(new Evento.AvisoGeral("bia entrou no chat."), Interpretador.interpretar("* bia entrou no chat."));
        assertEquals(new Evento.AvisoGrupo("estudo", "bia entrou no grupo."),
                Interpretador.interpretar(Protocolo.avisoGrupo("estudo", "bia entrou no grupo.")));
    }

    @Test
    void cabecalhosDeHistorico() {
        assertEquals(new Evento.CabecalhoGeral("Últimas 3 mensagens"),
                Interpretador.interpretar("--- Últimas 3 mensagens ---"));
        assertEquals(new Evento.CabecalhoGrupo("estudo", "fim do histórico"),
                Interpretador.interpretar(Protocolo.cabecalhoGrupo("estudo", "fim do histórico")));
        assertEquals(new Evento.CabecalhoPrivado("bia", "últimas 2 mensagens"),
                Interpretador.interpretar(Protocolo.cabecalhoPrivado("bia", "últimas 2 mensagens")));
    }

    @Test
    void erro() {
        assertEquals(new Evento.Erro("O usuário 'bia' não está online."),
                Interpretador.interpretar("! O usuário 'bia' não está online."));
    }

    @Test
    void linhasDeControle() {
        assertEquals(new Evento.Usuarios(List.of("ana", "bia", "UCI")),
                Interpretador.interpretar(Protocolo.usuarios(List.of("ana", "bia", "UCI"))));
        assertEquals(new Evento.Usuarios(List.of()), Interpretador.interpretar(Protocolo.usuarios(List.of())));
        assertEquals(new Evento.Grupos(List.of("estudo"), List.of("estudo", "time")),
                Interpretador.interpretar(Protocolo.grupos(List.of("estudo"), List.of("estudo", "time"))));
        assertEquals(new Evento.Grupos(List.of(), List.of()), Interpretador.interpretar(Protocolo.grupos(List.of(), List.of())));
    }

    @Test
    void outrasLinhasViramTextoSimples() {
        assertEquals(new Evento.Outra("Conectados: ana, bia"), Interpretador.interpretar("Conectados: ana, bia"));
        assertEquals(new Evento.Outra(""), Interpretador.interpretar(null));
    }

    @Test
    void idaEVoltaDeCadaTipoDeMensagem() {
        assertEquals(new Evento.MensagemGeral("21:03:15", "ana", "oi: tudo bem?"),
                Interpretador.interpretar(new Mensagem("ana", "oi: tudo bem?", HORA).formatar()));
        assertEquals(new Evento.MensagemGrupo("21:03:15", "estudo", "ana", "oi: tudo bem?"),
                Interpretador.interpretar(Mensagem.grupo("estudo", "ana", "oi: tudo bem?", HORA).formatar()));
        assertEquals(new Evento.MensagemPrivada("21:03:15", "ana", "bia", "oi: tudo bem?"),
                Interpretador.interpretar(Mensagem.privada("bia", "ana", "oi: tudo bem?", HORA).formatar()));
    }

    /**
     * Segurança: o texto digitado por um usuário vem sempre depois de "[hora] nome: ", então ele
     * nunca consegue se passar por erro, aviso ou linha de controle.
     */
    @Test
    void textoDoUsuarioNaoConsegueFalsificarEventosDoSistema() {
        String[] truques = {"! erro falso", "* aviso falso", "@@USUARIOS|invasor", "--- cabeçalho falso ---",
                "[21:03:15] bia: outra pessoa falando", "x -> y (privado): falso", "#grupo bia: falso"};
        for (String truque : truques) {
            Evento e = Interpretador.interpretar(new Mensagem("ana", truque, HORA).formatar());
            assertTrue(e instanceof Evento.MensagemGeral, "deveria ser mensagem geral: " + truque);
            Evento.MensagemGeral m = (Evento.MensagemGeral) e;
            assertEquals("ana", m.remetente(), truque);
            assertEquals(truque, m.texto(), truque);

            Evento g = Interpretador.interpretar(Mensagem.grupo("estudo", "ana", truque, HORA).formatar());
            assertTrue(g instanceof Evento.MensagemGrupo, "deveria ser mensagem de grupo: " + truque);
            assertEquals(truque, ((Evento.MensagemGrupo) g).texto(), truque);

            Evento p = Interpretador.interpretar(Mensagem.privada("bia", "ana", truque, HORA).formatar());
            assertTrue(p instanceof Evento.MensagemPrivada, "deveria ser mensagem privada: " + truque);
            assertEquals(truque, ((Evento.MensagemPrivada) p).texto(), truque);
        }
    }
}
