package br.unicesumar.chat.server;

import br.unicesumar.chat.model.StatusUsuario;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concorrência da sala: várias threads conversando, mudando de status e avisando "digitando" ao
 * mesmo tempo. Repetido 10 vezes porque erros de concorrência aparecem só de vez em quando.
 * Sem {@code Thread.sleep}: as threads largam juntas por um {@link CountDownLatch} e o teste espera
 * por {@code awaitTermination}.
 */
class SalaChatConcorrenciaTest {

    private static final int USUARIOS = 12;
    private static final int MENSAGENS_POR_USUARIO = 25;

    @RepeatedTest(10)
    void todosRecebemTodasAsMensagensNaMesmaOrdemMesmoComStatusEDigitandoConcorrentes() throws Exception {
        RepositorioEmMemoria repositorio = new RepositorioEmMemoria();
        SalaChat sala = new SalaChat(repositorio, 20);
        ComandosChat comandos = new ComandosChat(sala);
        List<DestinatarioFalso> clientes = new ArrayList<>();
        for (int i = 0; i < USUARIOS; i++) {
            DestinatarioFalso d = new DestinatarioFalso("user" + i);
            sala.entrar(d);
            d.ativarControle();
            clientes.add(d);
        }
        clientes.forEach(d -> d.recebidas().clear());

        ExecutorService pool = Executors.newFixedThreadPool(USUARIOS);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger falhas = new AtomicInteger();
        for (DestinatarioFalso d : clientes) {
            pool.execute(() -> {
                try {
                    largada.await();
                    for (int m = 0; m < MENSAGENS_POR_USUARIO; m++) {
                        comandos.executar(d, "msg " + m + " de " + d.getNome());
                        comandos.executar(d, "/digitando geral");
                        if (m % 5 == 0) {
                            comandos.executar(d, "/status " + (m % 10 == 0 ? "ocupado" : "disponivel"));
                        }
                        if (m % 7 == 0) {
                            comandos.executar(d, "/buscar msg");
                        }
                    }
                } catch (Exception e) {
                    falhas.incrementAndGet();
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "as threads deveriam terminar (sem deadlock)");

        assertEquals(0, falhas.get());
        assertEquals(USUARIOS * MENSAGENS_POR_USUARIO, repositorio.todas().size(), "nenhuma mensagem se perdeu");

        // Cada cliente recebe as mensagens dos OUTROS (o remetente não recebe a própria),
        // e a ordem relativa entre quaisquer duas mensagens é a mesma para todos (ordem do banco).
        List<String> ordemDoBanco = repositorio.todas().stream().map(m -> m.getRemetente() + ": " + m.getConteudo()).toList();
        for (DestinatarioFalso d : clientes) {
            List<String> recebidas = d.mensagens().stream().filter(l -> !l.contains("Busca")).toList();
            assertEquals((USUARIOS - 1) * MENSAGENS_POR_USUARIO, recebidas.size(), d.getNome() + " recebeu tudo");
            int anterior = -1;
            for (String linha : recebidas) {
                String chave = linha.substring(linha.indexOf("] ") + 2);
                int posicao = ordemDoBanco.indexOf(chave);
                assertTrue(posicao > anterior, d.getNome() + " viu mensagens fora da ordem: " + chave);
                anterior = posicao;
            }
        }
    }

    @RepeatedTest(10)
    void statusFinalEhConsistenteQuandoVariasThreadsTrocamEOUsuarioSai() throws Exception {
        SalaChat sala = new SalaChat(new RepositorioEmMemoria(), 20);
        DestinatarioFalso alvo = new DestinatarioFalso("alvo");
        sala.entrar(alvo);
        alvo.ativarControle();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        for (int t = 0; t < 8; t++) {
            final int numero = t;
            pool.execute(() -> {
                try {
                    largada.await();
                    for (int i = 0; i < 50; i++) {
                        sala.definirStatus(alvo, i % 2 == 0 ? StatusUsuario.OCUPADO : StatusUsuario.AUSENTE);
                    }
                    if (numero == 0) {
                        sala.sair(alvo);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // A thread 0 tirou "alvo" da sala; um status definido depois fica gravado, mas nunca é
        // divulgado para quem não está online — e nunca aparece uma linha mal formada.
        for (String linha : alvo.controle()) {
            assertTrue(linha.startsWith("@@"), linha);
            if (linha.startsWith("@@STATUS|")) {
                assertTrue(linha.equals("@@STATUS|") || linha.matches("@@STATUS\\|alvo=(ocupado|ausente)"), linha);
            }
        }
    }
}
