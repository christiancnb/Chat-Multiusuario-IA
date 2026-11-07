package br.unicesumar.chat.agente;

import br.unicesumar.chat.agente.Turno.Papel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cliente "compatível com OpenAI" (Ollama, Groq...) contra um servidor HTTP local de mentira. */
@Timeout(60)
class AgenteCompativelTest {

    private HttpServer servidor;
    private String urlBase;
    private volatile String metodo;
    private volatile String caminho;
    private volatile Map<String, String> cabecalhos;
    private volatile String corpoRecebido;
    private volatile int status = 200;
    private volatile String corpoDaResposta = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Oi!\"}}]}";

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", troca -> {
            metodo = troca.getRequestMethod();
            caminho = troca.getRequestURI().getPath();
            Map<String, String> h = new HashMap<>();
            troca.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
            cabecalhos = h;
            corpoRecebido = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = corpoDaResposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
        urlBase = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/";
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    private AgenteCompativel agente(String chave) {
        return new AgenteCompativel("Teste", urlBase, chave, "modelo-x");
    }

    private static List<Turno> conversa(String... falas) {
        List<Turno> t = new ArrayList<>();
        for (int i = 0; i < falas.length; i++) {
            t.add(new Turno(i % 2 == 0 ? Papel.USUARIO : Papel.AGENTE, falas[i]));
        }
        return t;
    }

    @Test
    void enviaNoFormatoChatCompletionsComPersonaComoPrimeiraMensagem() throws Exception {
        String r = agente("chave-123").responder(conversa("pergunta 1", "resposta 1", "pergunta 2"));

        assertEquals("Oi!", r);
        assertEquals("POST", metodo);
        assertEquals("/v1/chat/completions", caminho, "a barra final da URL base é ignorada");
        assertEquals("Bearer chave-123", cabecalhos.get("authorization"));
        Map<?, ?> corpo = (Map<?, ?>) Json.interpretar(corpoRecebido);
        assertEquals("modelo-x", corpo.get("model"));
        List<?> msgs = (List<?>) corpo.get("messages");
        assertEquals(4, msgs.size());
        assertEquals("system", ((Map<?, ?>) msgs.get(0)).get("role"));
        assertEquals(AgenteAnthropic.PERSONA, ((Map<?, ?>) msgs.get(0)).get("content"));
        assertEquals("user", ((Map<?, ?>) msgs.get(1)).get("role"));
        assertEquals("assistant", ((Map<?, ?>) msgs.get(2)).get("role"));
        assertEquals("pergunta 2", ((Map<?, ?>) msgs.get(3)).get("content"));
    }

    @Test
    void semChaveNaoMandaCabecalhoDeAutorizacao() throws Exception {
        agente("").responder(conversa("oi"));
        agente(null).responder(conversa("oi"));
        assertFalse(cabecalhos.containsKey("authorization"), "o Ollama local não usa chave");
    }

    @Test
    void erroDeAutenticacaoViraMensagemClaraSemVazarDetalhes() {
        status = 401;
        corpoDaResposta = "{\"error\":\"invalid key sk-segredo\"}";

        AgenteException e = assertThrows(AgenteException.class, () -> agente("x").responder(conversa("oi")));

        assertTrue(e.getMessage().contains("chave"));
        assertFalse(e.getMessage().contains("sk-segredo"), "detalhe técnico vai só para o log");
    }

    @Test
    void limiteDeUsoEIndisponibilidadeTemMensagensPropriasEOutrosStatusTambem() {
        status = 429;
        assertTrue(assertThrows(AgenteException.class, () -> agente("x").responder(conversa("oi")))
                .getMessage().contains("limite"));
        status = 503;
        assertTrue(assertThrows(AgenteException.class, () -> agente("x").responder(conversa("oi")))
                .getMessage().contains("indispon"));
    }

    @Test
    void respostaSemTextoOuIlegivelEErroControlado() {
        corpoDaResposta = "{\"choices\":[]}";
        assertThrows(AgenteException.class, () -> agente("x").responder(conversa("oi")));
        corpoDaResposta = "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}";
        assertThrows(AgenteException.class, () -> agente("x").responder(conversa("oi")));
        corpoDaResposta = "isto não é json";
        assertThrows(AgenteException.class, () -> agente("x").responder(conversa("oi")));
    }

    @Test
    void servidorForaDoArViraMensagemAmigavel() {
        servidor.stop(0);
        AgenteException e = assertThrows(AgenteException.class, () -> agente("").responder(conversa("oi")));
        assertTrue(e.getMessage().startsWith("Não consegui falar"));
    }

    @Test
    void tempoEsgotadoViraErroControlado() throws Exception {
        servidor.removeContext("/");
        servidor.createContext("/", troca -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
            troca.close();
        });
        AgenteCompativel curto = new AgenteCompativel("Teste", urlBase, "", "m", Duration.ofMillis(300));
        assertThrows(AgenteException.class, () -> curto.responder(conversa("oi")));
    }

    @Test
    void listaOsModelosDisponiveisOrdenadosEUsaAChave() {
        corpoDaResposta = "{\"data\":[{\"id\":\"zeta\"},{\"id\":\"alfa\"},{\"object\":\"x\"}]}";

        List<String> modelos = agente("k").listarModelos();

        assertEquals(List.of("alfa", "zeta"), modelos);
        assertEquals("GET", metodo);
        assertEquals("/v1/models", caminho);
        assertEquals("Bearer k", cabecalhos.get("authorization"));
    }

    @Test
    void listaDeModelosFalhaEmSilencioComListaVazia() {
        status = 500;
        assertTrue(agente("k").listarModelos().isEmpty());
        status = 200;
        corpoDaResposta = "lixo";
        assertTrue(agente("k").listarModelos().isEmpty());
        servidor.stop(0);
        assertTrue(agente("k").listarModelos().isEmpty());
    }

    // ---- configuração pelo ambiente ----

    private static Optional<AgenteCompativel> amb(String... pares) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return AgenteCompativel.deAmbiente(m::get);
    }

    @Test
    void semVariaveisNaoHaAgente() {
        assertTrue(amb().isEmpty());
        assertTrue(amb("UCI_PROVIDER", "  ").isEmpty());
    }

    @Test
    void atalhoOllamaUsaUrlEModeloLeveSemPrecisarDeChave() {
        AgenteCompativel a = amb("UCI_PROVIDER", "Ollama").orElseThrow();
        assertEquals("Ollama (modelo llama3.2:1b)", a.descricao());
    }

    @Test
    void atalhoGroqUsaPadroesEPermiteTrocarOModelo() {
        assertEquals("Groq (modelo llama-3.1-8b-instant)", amb("UCI_PROVIDER", "groq", "UCI_API_KEY", "k").orElseThrow().descricao());
        assertEquals("Groq (modelo outro)", amb("UCI_PROVIDER", "groq", "UCI_MODEL", "outro").orElseThrow().descricao());
    }

    @Test
    void urlPropriaExigeModelo() {
        assertThrows(IllegalArgumentException.class, () -> amb("UCI_API_URL", "https://x.exemplo/v1"));
        assertEquals("API compatível (modelo m)", amb("UCI_API_URL", "https://x.exemplo/v1", "UCI_MODEL", "m").orElseThrow().descricao());
    }

    @Test
    void atalhoDesconhecidoEhRecusadoComMensagemUtil() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> amb("UCI_PROVIDER", "xyz"));
        assertTrue(e.getMessage().contains("ollama"));
    }
}
