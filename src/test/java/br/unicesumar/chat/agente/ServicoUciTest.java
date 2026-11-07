package br.unicesumar.chat.agente;

import br.unicesumar.chat.agente.Turno.Papel;
import br.unicesumar.chat.model.Mensagem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
class ServicoUciTest {

    /** Ouvinte que guarda o desfecho para o teste aguardar. */
    private static final class Resultado implements ServicoUci.Ouvinte {
        final List<List<String>> respostas = new CopyOnWriteArrayList<>();
        final List<String> falhas = new CopyOnWriteArrayList<>();

        @Override
        public void aoResponder(List<String> partes) {
            respostas.add(partes);
        }

        @Override
        public void aoFalhar(String mensagemAmigavel) {
            falhas.add(mensagemAmigavel);
        }

        boolean terminou() {
            return !respostas.isEmpty() || !falhas.isEmpty();
        }
    }

    private static boolean esperar(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            if (condicao.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condicao.getAsBoolean();
    }

    @Test
    void respondeDeFormaAssincrona() throws Exception {
        try (ServicoUci uci = new ServicoUci(conversa -> "resposta")) {
            Resultado r = new Resultado();
            assertEquals(ServicoUci.Resultado.ACEITO, uci.perguntar("c1", "oi", r));
            assertTrue(esperar(r::terminou));
            assertEquals(List.of(List.of("resposta")), r.respostas);
            assertTrue(r.falhas.isEmpty());
        }
    }

    @Test
    void perguntarNaoBloqueiaMesmoComAgenteLento() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        try (ServicoUci uci = new ServicoUci(conversa -> {
            try {
                liberar.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        })) {
            long inicio = System.nanoTime();
            uci.perguntar("c1", "oi", new Resultado());
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            assertTrue(ms < 1000, "perguntar() deve só enfileirar, mas levou " + ms + " ms");
            liberar.countDown();
        }
    }

    @Test
    void recusaSegundaPerguntaDaMesmaConversaEnquantoAPrimeiraNaoTermina() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        try (ServicoUci uci = new ServicoUci(conversa -> {
            try {
                liberar.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        })) {
            Resultado primeira = new Resultado();
            assertEquals(ServicoUci.Resultado.ACEITO, uci.perguntar("c1", "a", primeira));
            assertEquals(ServicoUci.Resultado.OCUPADO, uci.perguntar("c1", "b", new Resultado()));
            assertEquals(ServicoUci.Resultado.ACEITO, uci.perguntar("c2", "c", new Resultado()),
                    "outra conversa não é afetada");

            liberar.countDown();
            assertTrue(esperar(primeira::terminou));
            // Depois do desfecho a conversa volta a aceitar perguntas (pode levar um instante).
            assertTrue(esperar(() -> uci.perguntar("c1", "d", new Resultado()) == ServicoUci.Resultado.ACEITO));
        }
    }

    @Test
    void recusaQuandoOPoolEAFilaEstaoCheios() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        try (ServicoUci uci = new ServicoUci(conversa -> {
            try {
                liberar.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        }, 1, 1)) {
            assertEquals(ServicoUci.Resultado.ACEITO, uci.perguntar("c1", "a", new Resultado())); // executando
            assertEquals(ServicoUci.Resultado.ACEITO, uci.perguntar("c2", "b", new Resultado())); // na fila
            assertEquals(ServicoUci.Resultado.SOBRECARREGADO, uci.perguntar("c3", "c", new Resultado()));
            liberar.countDown();
        }
    }

    @Test
    void falhaDoAgenteViraMensagemAmigavelSemDetalhesTecnicos() throws Exception {
        try (ServicoUci uci = new ServicoUci(conversa -> {
            throw new AgenteException("O serviço de IA está indisponível.", "HTTP 503: segredo-tecnico");
        })) {
            Resultado r = new Resultado();
            uci.perguntar("c1", "oi", r);
            assertTrue(esperar(r::terminou));
            assertEquals(List.of("O serviço de IA está indisponível."), r.falhas);
            assertTrue(r.respostas.isEmpty());
        }
    }

    @Test
    void erroInesperadoNoAgenteNaoDerrubaOServico() throws Exception {
        try (ServicoUci uci = new ServicoUci(conversa -> {
            throw new IllegalStateException("bug");
        })) {
            Resultado r = new Resultado();
            uci.perguntar("c1", "oi", r);
            assertTrue(esperar(r::terminou));
            assertEquals(1, r.falhas.size());
            assertTrue(esperar(() -> uci.perguntar("c1", "de novo", new Resultado()) == ServicoUci.Resultado.ACEITO),
                    "a conversa deve ser liberada mesmo após o erro");
        }
    }

    @Test
    void lembraDoQueFoiDitoNaMesmaConversa() throws Exception {
        List<List<Turno>> vistos = new CopyOnWriteArrayList<>();
        try (ServicoUci uci = new ServicoUci(conversa -> {
            vistos.add(new ArrayList<>(conversa));
            return "r" + vistos.size();
        })) {
            perguntarEEsperar(uci, "c1", "primeira");
            perguntarEEsperar(uci, "c1", "segunda");

            assertEquals(1, vistos.get(0).size());
            assertEquals(List.of(
                    new Turno(Papel.USUARIO, "primeira"),
                    new Turno(Papel.AGENTE, "r1"),
                    new Turno(Papel.USUARIO, "segunda")), vistos.get(1));
        }
    }

    @Test
    void conversasDiferentesTemMemoriasSeparadas() throws Exception {
        List<List<Turno>> vistos = new CopyOnWriteArrayList<>();
        try (ServicoUci uci = new ServicoUci(conversa -> {
            vistos.add(new ArrayList<>(conversa));
            return "ok";
        })) {
            perguntarEEsperar(uci, "c1", "segredo da conversa 1");
            perguntarEEsperar(uci, "c2", "olá");
            assertEquals(1, vistos.get(1).size(), "a conversa 2 não pode ver a conversa 1");
        }
    }

    @Test
    void esquecerApagaAMemoria() throws Exception {
        List<List<Turno>> vistos = new CopyOnWriteArrayList<>();
        try (ServicoUci uci = new ServicoUci(conversa -> {
            vistos.add(new ArrayList<>(conversa));
            return "ok";
        })) {
            perguntarEEsperar(uci, "c1", "primeira");
            uci.esquecer("c1");
            perguntarEEsperar(uci, "c1", "segunda");
            assertEquals(1, vistos.get(1).size());
        }
    }

    @Test
    void falhaNaoDeixaBuracoNaMemoria() throws Exception {
        List<List<Turno>> vistos = new CopyOnWriteArrayList<>();
        boolean[] primeira = {true};
        try (ServicoUci uci = new ServicoUci(conversa -> {
            vistos.add(new ArrayList<>(conversa));
            if (primeira[0]) {
                primeira[0] = false;
                throw new AgenteException("falhou", "detalhe");
            }
            return "ok";
        })) {
            perguntarEEsperar(uci, "c1", "vai falhar");
            perguntarEEsperar(uci, "c1", "vai dar certo");
            assertEquals(List.of(new Turno(Papel.USUARIO, "vai dar certo")), vistos.get(1));
        }
    }

    @Test
    void amemoriaTemTamanhoLimitado() throws Exception {
        List<List<Turno>> vistos = new CopyOnWriteArrayList<>();
        try (ServicoUci uci = new ServicoUci(conversa -> {
            vistos.add(new ArrayList<>(conversa));
            return "ok";
        })) {
            for (int i = 0; i < ServicoUci.MAX_FALAS + 5; i++) {
                perguntarEEsperar(uci, "c1", "p" + i);
            }
            int ultimo = vistos.get(vistos.size() - 1).size();
            assertTrue(ultimo <= ServicoUci.MAX_FALAS + 1, "contexto cresceu demais: " + ultimo);
            assertEquals(Papel.USUARIO, vistos.get(vistos.size() - 1).get(0).papel(),
                    "o contexto enviado deve começar por uma fala do usuário");
        }
    }

    private void perguntarEEsperar(ServicoUci uci, String conversa, String texto) throws Exception {
        Resultado r = new Resultado();
        assertTrue(esperar(() -> uci.perguntar(conversa, texto, r) == ServicoUci.Resultado.ACEITO));
        assertTrue(esperar(r::terminou));
    }

    // ---- dividir() ----

    @Test
    void respostaVaziaViraAvisoEmVezDeMensagemVazia() {
        assertEquals(List.of("(sem resposta)"), ServicoUci.dividir(null));
        assertEquals(List.of("(sem resposta)"), ServicoUci.dividir("  \n\t "));
    }

    @Test
    void respostaCurtaFicaIntacta() {
        assertEquals(List.of("Olá, tudo bem?"), ServicoUci.dividir("Olá, tudo bem?"));
    }

    @Test
    void quebrasDeLinhaEEspacosViramUmEspaco() {
        assertEquals(List.of("linha 1 linha 2 linha 3"), ServicoUci.dividir("linha 1\n\nlinha 2\r\n  linha 3  "));
    }

    @Test
    void respostaLongaEDivididaEmPartesQueCabemNoChat() {
        String longa = ("palavra ").repeat(120).trim(); // 959 caracteres
        List<String> partes = ServicoUci.dividir(longa);
        assertTrue(partes.size() > 1);
        for (String p : partes) {
            assertTrue(p.length() <= ServicoUci.TAMANHO_PARTE + 1, "parte grande demais: " + p.length());
            assertTrue(p.length() <= Mensagem.TAMANHO_MAXIMO);
            assertFalse(p.isBlank());
            assertFalse(p.contains("\n"));
        }
        assertEquals(longa, String.join(" ", partes), "nada pode se perder ao dividir");
    }

    @Test
    void cortaEmEspacosEntrePalavras() {
        List<String> partes = ServicoUci.dividir(("abcdefghi ").repeat(100).trim());
        for (String p : partes) {
            assertTrue(p.startsWith("abcdefghi") && p.endsWith("abcdefghi"), "cortou no meio de uma palavra: " + p);
        }
    }

    @Test
    void palavraGiganteEhCortadaNoLimite() {
        List<String> partes = ServicoUci.dividir("x".repeat(1000));
        assertEquals(ServicoUci.TAMANHO_PARTE, partes.get(0).length());
        assertTrue(partes.size() >= 2);
    }

    @Test
    void respostaEnormeEhLimitadaEMarcadaComReticencias() {
        List<String> partes = ServicoUci.dividir(("palavra ").repeat(5000));
        assertEquals(ServicoUci.MAX_PARTES, partes.size());
        assertTrue(partes.get(partes.size() - 1).endsWith("…"));
        for (String p : partes) {
            assertTrue(p.length() <= Mensagem.TAMANHO_MAXIMO);
        }
    }
}
