package br.unicesumar.chat.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportacaoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 3, 20, 15, 0);

    @Test
    void nomeSugeridoEhSeguroParaArquivo() {
        assertEquals("chat-estudo-20261103-201500.txt", Exportacao.nomeSugerido("#estudo", AGORA));
        assertEquals("chat-conversa-20261103-201500.txt", Exportacao.nomeSugerido("???", AGORA));
        assertEquals("chat-joão-20261103-201500.txt", Exportacao.nomeSugerido("João", AGORA));
    }

    @Test
    void montaCabecalhoEConteudo() {
        String texto = Exportacao.montar("Geral", "[10:00:00] ana: oi\n[10:00:05] bia: olá\n", AGORA);

        assertTrue(texto.startsWith("Chat Multiusuário — Geral"));
        assertTrue(texto.contains("Exportado em 03/11/2026 20:15:00"));
        assertTrue(texto.contains("[10:00:00] ana: oi"));
        assertTrue(texto.contains("[10:00:05] bia: olá"));
    }

    @Test
    void conversaVaziaFicaIndicada() {
        assertTrue(Exportacao.montar("Geral", "", AGORA).contains("(conversa vazia)"));
        assertTrue(Exportacao.montar("Geral", null, AGORA).contains("(conversa vazia)"));
    }

    @Test
    void salvaEmUtf8CriandoPastas() throws IOException {
        Path pasta = Files.createTempDirectory("exportacao");
        Path arquivo = pasta.resolve("sub").resolve("conversa.txt");

        Exportacao.salvar(arquivo, "Geral", "[10:00:00] ana: ação rápida\n", AGORA);

        String lido = Files.readString(arquivo, StandardCharsets.UTF_8);
        assertTrue(lido.contains("ana: ação rápida"));
    }

    @Test
    void falhaDeGravacaoVirouExcecao() throws IOException {
        Path pasta = Files.createTempDirectory("exportacao");
        Path ocupado = pasta.resolve("arquivo");
        Files.writeString(ocupado, "x");

        // "arquivo" é um arquivo, então não pode virar pasta
        assertThrows(IOException.class, () ->
                Exportacao.salvar(ocupado.resolve("filho.txt"), "Geral", "x", AGORA));
    }
}
