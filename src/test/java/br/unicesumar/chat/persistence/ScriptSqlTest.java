package br.unicesumar.chat.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** O script entregue em sql/criar_tabelas.sql e o usado pelo servidor (schema.sql) nunca podem divergir. */
class ScriptSqlTest {

    @Test
    void scriptDaEntregaEhIgualAoDoClasspath() throws IOException {
        String entregue = Files.readString(Path.of("sql", "criar_tabelas.sql"), StandardCharsets.UTF_8);
        String usado;
        try (var in = getClass().getResourceAsStream("/schema.sql")) {
            usado = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(usado.replace("\r\n", "\n"), entregue.replace("\r\n", "\n"));
    }
}
