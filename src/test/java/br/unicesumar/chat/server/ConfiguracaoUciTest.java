package br.unicesumar.chat.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfiguracaoUciTest {

    private static final Logger LOG = Logger.getLogger("teste");

    private static Function<String, String> env(Map<String, String> m) {
        return m::get;
    }

    @Test
    void arquivoValeQuandoNaoHaVariavelDeAmbiente() throws Exception {
        Path f = Files.createTempFile("uci", ".properties");
        Files.writeString(f, "UCI_PROVIDER=ollama\n#UCI_MODEL=comentado\nUCI_API_KEY=  \n");

        Function<String, String> c = ConfiguracaoUci.combinar(env(Map.of()), f, LOG);

        assertEquals("ollama", c.apply("UCI_PROVIDER"));
        assertNull(c.apply("UCI_MODEL"), "linha comentada é ignorada");
        assertNull(c.apply("UCI_API_KEY"), "valor em branco conta como ausente");
        Files.delete(f);
    }

    @Test
    void ambienteTemPrioridadeSobreOArquivo() throws Exception {
        Path f = Files.createTempFile("uci", ".properties");
        Files.writeString(f, "UCI_PROVIDER=ollama\nUCI_MODEL=do-arquivo\n");

        Function<String, String> c = ConfiguracaoUci.combinar(env(Map.of("UCI_PROVIDER", "groq")), f, LOG);

        assertEquals("groq", c.apply("UCI_PROVIDER"));
        assertEquals("do-arquivo", c.apply("UCI_MODEL"));
        Files.delete(f);
    }

    @Test
    void arquivoInexistenteNaoDaErro() {
        Function<String, String> c = ConfiguracaoUci.combinar(env(Map.of("A", "1")),
                Path.of("nao-existe-" + System.nanoTime() + ".properties"), LOG);
        assertEquals("1", c.apply("A"));
        assertNull(c.apply("B"));
    }

    @Test
    void arquivoComAcentosEhLidoEmUtf8() throws Exception {
        Path f = Files.createTempFile("uci", ".properties");
        Files.writeString(f, "UCI_MODEL=modelo-é-ç\n", java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("modelo-é-ç", ConfiguracaoUci.combinar(env(Map.of()), f, LOG).apply("UCI_MODEL"));
        Files.delete(f);
    }
}
