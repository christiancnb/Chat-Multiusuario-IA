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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o cliente da API da Anthropic contra um servidor HTTP local de mentira (nenhuma chave
 * real nem internet): confere o que é enviado, como a resposta é lida e como os erros viram mensagens.
 */
@Timeout(60)
class AgenteAnthropicTest {

    private static final String CHAVE = "sk-ant-chave-de-teste-123";

    private HttpServer servidor;
    private String urlBase;

    // O que o servidor de mentira recebeu e o que ele deve responder.
    private volatile String metodo;
    private volatile String caminho;
    private volatile Map<String, String> cabecalhos;
    private volatile String corpoRecebido;
    private volatile int statusDaResposta = 200;
    private volatile String corpoDaResposta = "{\"content\":[{\"type\":\"text\",\"text\":\"Olá!\"}]}";
    private volatile long atrasoMs;
    private final AtomicInteger chamadas = new AtomicInteger();

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", troca -> {
            chamadas.incrementAndGet();
            metodo = troca.getRequestMethod();
            caminho = troca.getRequestURI().getPath();
            Map<String, String> h = new java.util.HashMap<>();
            troca.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
            cabecalhos = h;
            corpoRecebido = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (atrasoMs > 0) {
                try {
                    Thread.sleep(atrasoMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = corpoDaResposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            try {
                troca.sendResponseHeaders(statusDaResposta, bytes.length);
                troca.getResponseBody().write(bytes);
            } catch (IOException e) {
                // cliente desistiu (teste de tempo esgotado)
            } finally {
                troca.close();
            }
        });
        servidor.start();
        urlBase = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    private AgenteAnthropic agente() {
        return new AgenteAnthropic(CHAVE, "modelo-de-teste", urlBase);
    }

    private static List<Turno> conversa(String... falas) {
        List<Turno> t = new ArrayList<>();
        for (int i = 0; i < falas.length; i++) {
            t.add(new Turno(i % 2 == 0 ? Papel.USUARIO : Papel.AGENTE, falas[i]));
        }
        return t;
    }

    @Test
    void enviaORequisicaoNoFormatoDaApi() throws Exception {
        String resposta = agente().responder(conversa("Qual a capital do Brasil?"));

        assertEquals("Olá!", resposta);
        assertEquals("POST", metodo);
        assertEquals("/v1/messages", caminho);
        assertEquals(CHAVE, cabecalhos.get("x-api-key"));
        assertEquals("2023-06-01", cabecalhos.get("anthropic-version"));
        assertTrue(cabecalhos.get("content-type").startsWith("application/json"));

        Map<?, ?> corpo = (Map<?, ?>) Json.interpretar(corpoRecebido);
        assertEquals("modelo-de-teste", corpo.get("model"));
        assertEquals((double) AgenteAnthropic.MAX_TOKENS, corpo.get("max_tokens"));
        assertEquals(AgenteAnthropic.PERSONA, corpo.get("system"));
        List<?> mensagens = (List<?>) corpo.get("messages");
        assertEquals(1, mensagens.size());
        assertEquals("user", ((Map<?, ?>) mensagens.get(0)).get("role"));
        assertEquals("Qual a capital do Brasil?", ((Map<?, ?>) mensagens.get(0)).get("content"));
    }

    @Test
    void historicoDaConversaVaiComPapeisAlternados() throws Exception {
        agente().responder(conversa("pergunta 1", "resposta 1", "pergunta 2"));

        List<?> mensagens = (List<?>) ((Map<?, ?>) Json.interpretar(corpoRecebido)).get("messages");
        assertEquals(3, mensagens.size());
        assertEquals("user", ((Map<?, ?>) mensagens.get(0)).get("role"));
        assertEquals("assistant", ((Map<?, ?>) mensagens.get(1)).get("role"));
        assertEquals("resposta 1", ((Map<?, ?>) mensagens.get(1)).get("content"));
        assertEquals("user", ((Map<?, ?>) mensagens.get(2)).get("role"));
    }

    @Test
    void caracteresEspeciaisChegamIntactos() throws Exception {
        String texto = "aspas \" barra \\ acento é ç ã emoji \uD83D\uDE00 e {chaves} [colchetes]";
        agente().responder(conversa(texto));

        List<?> mensagens = (List<?>) ((Map<?, ?>) Json.interpretar(corpoRecebido)).get("messages");
        assertEquals(texto, ((Map<?, ?>) mensagens.get(0)).get("content"));
    }

    @Test
    void urlBaseComBarraNoFinalFunciona() throws Exception {
        new AgenteAnthropic(CHAVE, "m", urlBase + "///").responder(conversa("oi"));
        assertEquals("/v1/messages", caminho);
    }

    @Test
    void juntaOsBlocosDeTextoEIgnoraOsDemais() throws Exception {
        corpoDaResposta = "{\"content\":[{\"type\":\"text\",\"text\":\"Parte 1. \"},"
                + "{\"type\":\"tool_use\",\"id\":\"x\",\"name\":\"f\",\"input\":{}},"
                + "{\"type\":\"text\",\"text\":\"Parte 2.\"}],\"stop_reason\":\"end_turn\"}";
        assertEquals("Parte 1. Parte 2.", agente().responder(conversa("oi")));
    }

    @Test
    void leTextoDaRespostaComEscapesEUnicode() throws Exception {
        corpoDaResposta = "{\"content\":[{\"type\":\"text\",\"text\":\"Linha\\ncom \\\"aspas\\\" e \\u00e7\"}]}";
        assertEquals("Linha\ncom \"aspas\" e ç", agente().responder(conversa("oi")));
    }

    @Test
    void erroDeChaveInvalidaViraMensagemClara() {
        statusDaResposta = 401;
        corpoDaResposta = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        AgenteException e = assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")));
        assertTrue(e.getMessage().contains("chave"), e.getMessage());
        assertTrue(e.getDetalhe().contains("401"));
    }

    @Test
    void limiteDeUsoServicoForaDoArEOutrosErros() {
        statusDaResposta = 429;
        assertTrue(assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")))
                .getMessage().contains("limite"));

        statusDaResposta = 503;
        assertTrue(assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")))
                .getMessage().contains("indisponível"));

        statusDaResposta = 400;
        assertTrue(assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")))
                .getMessage().contains("400"));
    }

    @Test
    void respostaQueNaoEJsonVirErroSemEstourar() {
        corpoDaResposta = "<html>proxy corporativo</html>";
        AgenteException e = assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")));
        assertTrue(e.getMessage().contains("ilegível"), e.getMessage());
    }

    @Test
    void respostaSemTextoVirErro() {
        corpoDaResposta = "{\"content\":[]}";
        assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")));
        corpoDaResposta = "{\"content\":[{\"type\":\"text\",\"text\":\"   \"}]}";
        assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")));
        corpoDaResposta = "[]";
        assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")));
    }

    @Test
    void tempoEsgotadoViraErroDeRede() {
        atrasoMs = 1500;
        AgenteAnthropic lento = new AgenteAnthropic(CHAVE, "m", urlBase, Duration.ofMillis(300));
        AgenteException e = assertThrows(AgenteException.class, () -> lento.responder(conversa("oi")));
        assertTrue(e.getMessage().contains("Não consegui falar"), e.getMessage());
    }

    @Test
    void servidorInacessivelViraErroDeRede() {
        servidor.stop(0);
        AgenteException e = assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")));
        assertTrue(e.getMessage().contains("Não consegui falar"), e.getMessage());
    }

    @Test
    void aChaveDaApiNuncaApareceNasMensagensDeErro() {
        int[] status = {400, 401, 403, 429, 500, 503};
        for (int s : status) {
            statusDaResposta = s;
            corpoDaResposta = "{\"error\":{\"message\":\"x-api-key " + CHAVE + " inválida\"}}"; // um servidor "vazando" a chave
            AgenteException e = assertThrows(AgenteException.class, () -> agente().responder(conversa("oi")));
            assertFalse(e.getMessage().contains(CHAVE), "a mensagem para os usuários não pode conter a chave (HTTP " + s + ")");
        }
    }

    // ---- alternar() ----

    @Test
    void alternarJuntaFalasSeguidasDoMesmoPapel() {
        List<Turno> r = AgenteAnthropic.alternar(List.of(
                new Turno(Papel.USUARIO, "ana: oi"),
                new Turno(Papel.USUARIO, "bia: olá"),
                new Turno(Papel.AGENTE, "oi, pessoal"),
                new Turno(Papel.USUARIO, "ana: obrigada")));
        assertEquals(3, r.size());
        assertEquals("ana: oi\nbia: olá", r.get(0).texto());
        assertEquals(Papel.AGENTE, r.get(1).papel());
    }

    @Test
    void alternarDescartaFalasDoAgenteNoComecoPoisAApiExigeComecarPorUsuario() {
        List<Turno> r = AgenteAnthropic.alternar(List.of(
                new Turno(Papel.AGENTE, "sobra do passado"),
                new Turno(Papel.USUARIO, "oi")));
        assertEquals(List.of(new Turno(Papel.USUARIO, "oi")), r);
    }

    @Test
    void alternarExigeAoMenosUmaFalaDoUsuario() {
        assertThrows(IllegalArgumentException.class, () -> AgenteAnthropic.alternar(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> AgenteAnthropic.alternar(List.of(new Turno(Papel.AGENTE, "só eu"))));
    }

    // ---- configuração ----

    @Test
    void semChaveNoAmbienteNaoCriaAgente() {
        assertEquals(Optional.empty(), AgenteAnthropic.deAmbiente(nome -> null));
        assertEquals(Optional.empty(), AgenteAnthropic.deAmbiente(nome -> "   "));
    }

    @Test
    void comChaveNoAmbienteUsaModeloEUrlConfiguradosOuOsPadroes() throws Exception {
        AgenteAnthropic padrao = AgenteAnthropic.deAmbiente(
                nome -> nome.equals("ANTHROPIC_API_KEY") ? CHAVE : null).orElseThrow();
        assertTrue(padrao.descricao().contains(AgenteAnthropic.MODELO_PADRAO));

        AgenteAnthropic custom = AgenteAnthropic.deAmbiente(nome -> switch (nome) {
            case "ANTHROPIC_API_KEY" -> CHAVE;
            case "UCI_MODEL" -> "outro-modelo";
            case "ANTHROPIC_BASE_URL" -> urlBase;
            default -> null;
        }).orElseThrow();
        assertTrue(custom.descricao().contains("outro-modelo"));
        assertEquals("Olá!", custom.responder(conversa("oi")));
        assertEquals(1, chamadas.get());
    }

    @Test
    void chaveVaziaNoConstrutorEInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new AgenteAnthropic("", "m", urlBase));
        assertThrows(IllegalArgumentException.class, () -> new AgenteAnthropic(null, "m", urlBase));
    }

    @Test
    void descricaoNaoRevelaAChave() {
        assertFalse(agente().descricao().contains(CHAVE));
    }
}
