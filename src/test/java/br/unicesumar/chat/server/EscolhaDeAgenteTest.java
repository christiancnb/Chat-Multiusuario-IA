package br.unicesumar.chat.server;

import br.unicesumar.chat.agente.AgenteAnthropic;
import br.unicesumar.chat.agente.AgenteCompativel;
import br.unicesumar.chat.agente.AgenteLocal;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EscolhaDeAgenteTest {

    private static Function<String, String> amb(String... pares) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return m::get;
    }

    @Test
    void semNadaUsaOModoLocal() {
        assertInstanceOf(AgenteLocal.class, Servidor.escolherAgente(amb()));
    }

    @Test
    void somenteAnthropicUsaAnthropic() {
        assertInstanceOf(AgenteAnthropic.class, Servidor.escolherAgente(amb("ANTHROPIC_API_KEY", "k")));
    }

    @Test
    void providerTemPrioridadeSobreAnthropic() {
        assertInstanceOf(AgenteCompativel.class,
                Servidor.escolherAgente(amb("UCI_PROVIDER", "ollama", "ANTHROPIC_API_KEY", "k")));
    }

    @Test
    void configuracaoInvalidaNaoDerrubaOServidorECaiNoModoLocal() {
        assertInstanceOf(AgenteLocal.class, Servidor.escolherAgente(amb("UCI_PROVIDER", "xyz")));
        assertInstanceOf(AgenteLocal.class, Servidor.escolherAgente(amb("UCI_API_URL", "http://x/v1"))); // sem modelo
    }
}
