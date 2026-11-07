package br.unicesumar.chat.protocolo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ProtocoloStatusDigitandoTest {

    @Test
    void statusIdaEVolta() {
        Map<String, String> mapa = new LinkedHashMap<>();
        mapa.put("ana", "ocupado");
        mapa.put("bia", "ausente");

        String linha = Protocolo.status(mapa);
        Evento.Status evento = assertInstanceOf(Evento.Status.class, Interpretador.interpretar(linha));

        assertEquals("@@STATUS|ana=ocupado,bia=ausente", linha);
        assertEquals(mapa, evento.porUsuario());
    }

    @Test
    void statusVazioViraMapaVazio() {
        Evento.Status evento = assertInstanceOf(Evento.Status.class, Interpretador.interpretar("@@STATUS|"));
        assertEquals(Map.of(), evento.porUsuario());
    }

    @Test
    void statusIgnoraParesMalFormados() {
        Evento.Status evento = assertInstanceOf(Evento.Status.class,
                Interpretador.interpretar("@@STATUS|ana=ocupado,lixo,=x,Bia=ausente"));
        assertEquals(Map.of("ana", "ocupado", "bia", "ausente"), evento.porUsuario());
    }

    @Test
    void digitandoIdaEVolta() {
        Evento.Digitando e = assertInstanceOf(Evento.Digitando.class,
                Interpretador.interpretar(Protocolo.digitando("ana", "#estudo")));
        assertEquals("ana", e.quem());
        assertEquals("#estudo", e.conversa());
    }

    @Test
    void digitandoMalFormadoViraOutra() {
        assertInstanceOf(Evento.Outra.class, Interpretador.interpretar("@@DIGITANDO|ana"));
    }

    @Test
    void textoDeUsuarioNuncaViraControle() {
        // uma mensagem real sempre começa com "[hora] nome:", então nunca começa com @@
        assertInstanceOf(Evento.MensagemGeral.class,
                Interpretador.interpretar("[10:00:00] ana: @@STATUS|bia=ausente"));
    }
}
