package br.unicesumar.chat.agente;

import br.unicesumar.chat.agente.Turno.Papel;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgenteLocalTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-08T17:30:00Z"), ZoneId.of("UTC"));
    private final AgenteLocal agente = new AgenteLocal(RELOGIO);

    private String perguntar(String texto) throws AgenteException {
        return agente.responder(List.of(new Turno(Papel.USUARIO, texto)));
    }

    @Test
    void cumprimenta() throws Exception {
        assertTrue(perguntar("Oi!").startsWith("Olá!"));
        assertTrue(perguntar("Olá, tudo bem?").startsWith("Olá!"));
        assertTrue(perguntar("BOM DIA").startsWith("Olá!"));
    }

    @Test
    void naoConfundePalavraInteiraComParteDeOutra() throws Exception {
        assertTrue(perguntar("o boi comeu o capim").contains("modo local"), "\"oi\" dentro de \"boi\" não é cumprimento");
    }

    @Test
    void respondeHoraEData() throws Exception {
        assertEquals("Agora são 17:30.", perguntar("Que horas são?"));
        assertEquals("Hoje é 08/10/2026.", perguntar("qual a data de hoje"));
    }

    @Test
    void explicaOsComandosNaAjuda() throws Exception {
        String ajuda = perguntar("preciso de ajuda");
        assertTrue(ajuda.contains("/msg"));
        assertTrue(ajuda.contains("/grupo criar"));
        assertTrue(ajuda.contains("@UCI"));
    }

    @Test
    void apresentaESeDespede() throws Exception {
        assertTrue(perguntar("quem é você?").contains("UCI"));
        assertTrue(perguntar("Muito obrigada!").startsWith("De nada"));
    }

    @Test
    void admiteQuandoNaoEntende() throws Exception {
        assertTrue(perguntar("explique a teoria da relatividade").contains("modo local"));
    }

    @Test
    void usaSomenteAUltimaFalaDoUsuario() throws Exception {
        String resposta = agente.responder(List.of(
                new Turno(Papel.USUARIO, "que horas são?"),
                new Turno(Papel.AGENTE, "Agora são 17:30."),
                new Turno(Papel.USUARIO, "obrigado")));
        assertTrue(resposta.startsWith("De nada"));
    }

    @Test
    void conversaSemFalaDoUsuarioEErro() {
        assertThrows(AgenteException.class, () -> agente.responder(List.of()));
        assertThrows(AgenteException.class, () -> agente.responder(List.of(new Turno(Papel.AGENTE, "oi"))));
    }

    @Test
    void normalizarRemoveAcentosEPontuacao() {
        assertEquals(" ola tudo bem ", AgenteLocal.normalizar("Olá, tudo bem?!"));
    }
}
