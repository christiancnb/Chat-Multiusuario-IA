package br.unicesumar.chat.agente;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void leObjetoComTodosOsTiposDeValor() {
        Object raiz = Json.interpretar("{\"a\":1,\"b\":\"x\",\"c\":true,\"d\":false,\"e\":null,\"f\":[1,2],\"g\":{\"h\":-2.5e1}}");
        Map<?, ?> m = (Map<?, ?>) raiz;
        assertEquals(1.0, m.get("a"));
        assertEquals("x", m.get("b"));
        assertEquals(Boolean.TRUE, m.get("c"));
        assertEquals(Boolean.FALSE, m.get("d"));
        assertNull(m.get("e"));
        assertEquals(List.of(1.0, 2.0), m.get("f"));
        assertEquals(-25.0, ((Map<?, ?>) m.get("g")).get("h"));
    }

    @Test
    void ignoraEspacosEQuebrasDeLinha() {
        Map<?, ?> m = (Map<?, ?>) Json.interpretar(" \n{ \"a\" : [ 1 , 2 ] }\n ");
        assertEquals(List.of(1.0, 2.0), m.get("a"));
    }

    @Test
    void leEscapesDeString() {
        assertEquals("a\"b\\c/d\ne\tf\u00e9", Json.interpretar("\"a\\\"b\\\\c\\/d\\ne\\tf\\u00e9\""));
    }

    @Test
    void objetoEListaVazios() {
        assertEquals(Map.of(), Json.interpretar("{}"));
        assertEquals(List.of(), Json.interpretar("[]"));
    }

    @Test
    void textoGeraStringJsonEscapadaQueVoltaIgual() {
        String original = "aspas \" barra \\ nova\nlinha\ttab \u0001 acento é emoji \uD83D\uDE00";
        String json = Json.texto(original);
        assertTrue(json.startsWith("\"") && json.endsWith("\""));
        assertTrue(json.indexOf('\n') < 0, "não pode haver quebra de linha crua dentro do JSON");
        assertEquals(original, Json.interpretar(json));
    }

    @Test
    void rejeitaJsonInvalido() {
        String[] invalidos = {"", "{", "[1,", "{\"a\"}", "{\"a\":}", "{a:1}", "\"aberta", "tru", "[1 2]",
                "{\"a\":1} lixo", "\"\\x\"", "\"\\u12\"", "--1"};
        for (String json : invalidos) {
            assertThrows(IllegalArgumentException.class, () -> Json.interpretar(json), "deveria rejeitar: " + json);
        }
        assertThrows(IllegalArgumentException.class, () -> Json.interpretar(null));
    }

    @Test
    void limitaAProfundidadeParaNaoEstourarAPilha() {
        String fundo = "[".repeat(1000) + "]".repeat(1000);
        assertThrows(IllegalArgumentException.class, () -> Json.interpretar(fundo));
    }
}
