package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testa broadcast, histórico e concorrência da sala, sem rede e sem banco real. */
@Timeout(30)
class SalaChatTest {

    private RepositorioEmMemoria repositorio;
    private SalaChat sala;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEmMemoria();
        sala = new SalaChat(repositorio, 3);
    }

    @Test
    void mensagemChegaATodosOsDemaisMasNaoAoRemetente() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        DestinatarioFalso caio = conectar("caio");

        sala.publicar(ana, "olá, turma");

        assertEquals(1, bia.conversa().size());
        assertTrue(bia.conversa().get(0).endsWith("ana: olá, turma"));
        assertEquals(bia.conversa(), caio.conversa());
        assertTrue(ana.conversa().isEmpty());
    }

    @Test
    void nomeEmUsoERecusadoSemDiferenciarMaiusculas() {
        assertTrue(sala.entrar(new DestinatarioFalso("Ana")));
        assertFalse(sala.entrar(new DestinatarioFalso("ana")));
        assertEquals(1, sala.totalConectados());
    }

    @Test
    void mensagemEPersistidaAntesDoBroadcast() {
        DestinatarioFalso ana = conectar("ana");
        conectar("bia");

        Mensagem registrada = sala.publicar(ana, "guardada");

        assertEquals(1L, registrada.getId());
        assertEquals(1, repositorio.todas().size());
        assertEquals("guardada", repositorio.todas().get(0).getConteudo());
    }

    @Test
    void novoClienteRecebeApenasAsUltimasNMensagensEmOrdem() {
        DestinatarioFalso ana = conectar("ana");
        for (int i = 1; i <= 5; i++) {
            sala.publicar(ana, "msg " + i);
        }

        DestinatarioFalso bia = conectar("bia");

        List<String> historico = bia.conversa();
        assertEquals(3, historico.size());
        assertTrue(historico.get(0).endsWith("ana: msg 3"));
        assertTrue(historico.get(2).endsWith("ana: msg 5"));
    }

    @Test
    void semHistoricoOClienteRecebeAvisoDeSalaVazia() {
        DestinatarioFalso ana = conectar("ana");
        assertEquals("--- Nenhuma mensagem anterior ---", ana.recebidas().get(0));
    }

    @Test
    void clienteQueCaiESubstituidoSemAfetarOsOutros() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        DestinatarioFalso caio = conectar("caio");
        bia.simularQueda();

        sala.publicar(ana, "alguém aí?");

        assertEquals(2, sala.totalConectados());
        assertFalse(sala.listarNomes().contains("bia"));
        assertEquals(1, caio.conversa().size());
        assertTrue(caio.recebidas().stream().anyMatch(l -> l.equals("* bia saiu do chat.")));
    }

    @Test
    void sairRemoveEAvisaOsDemais() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        assertTrue(sala.sair(bia));
        assertFalse(sala.sair(bia)); // segunda saída não tem efeito

        assertEquals(List.of("ana"), sala.listarNomes());
        assertTrue(ana.recebidas().contains("* bia saiu do chat."));
    }

    @Test
    void falhaNoBancoNaoImpedeARetransmissao() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        repositorio.falharSempre();

        sala.publicar(ana, "sem banco");

        assertEquals(1, bia.conversa().size());
    }

    @Test
    void conteudoInvalidoELancadoENaoEhRetransmitido() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        assertThrows(IllegalArgumentException.class, () -> sala.publicar(ana, "   "));

        assertTrue(bia.conversa().isEmpty());
        assertTrue(repositorio.todas().isEmpty());
    }

    @Test
    void listarNomesEmOrdemAlfabetica() {
        conectar("caio");
        conectar("ana");
        conectar("Bia");
        assertEquals(List.of("ana", "Bia", "caio"), sala.listarNomes());
    }

    @Test
    void publicacoesConcorrentesNaoPerdemNemEmbaralhamMensagens() throws Exception {
        final int remetentes = 8;
        final int porRemetente = 50;
        final int total = remetentes * porRemetente;

        SalaChat salaGrande = new SalaChat(repositorio, 20);
        DestinatarioFalso observador1 = new DestinatarioFalso("obs1");
        DestinatarioFalso observador2 = new DestinatarioFalso("obs2");
        salaGrande.entrar(observador1);
        salaGrande.entrar(observador2);

        List<DestinatarioFalso> autores = new ArrayList<>();
        for (int i = 0; i < remetentes; i++) {
            DestinatarioFalso autor = new DestinatarioFalso("autor" + i);
            salaGrande.entrar(autor);
            autores.add(autor);
        }

        ExecutorService pool = Executors.newFixedThreadPool(remetentes);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> tarefas = new ArrayList<>();
        for (DestinatarioFalso autor : autores) {
            tarefas.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < porRemetente; i++) {
                    salaGrande.publicar(autor, "n" + i);
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> tarefa : tarefas) {
            tarefa.get(20, TimeUnit.SECONDS); // propaga qualquer exceção ocorrida nas threads
        }
        pool.shutdown();

        assertEquals(total, repositorio.todas().size(), "todas devem ser gravadas");
        assertEquals(total, observador1.conversa().size(), "nenhuma pode se perder no broadcast");
        assertEquals(observador1.conversa(), observador2.conversa(),
                "todos os clientes veem as mensagens na mesma ordem");
    }

    @Test
    void entradasESaidasConcorrentesMantemAListaConsistente() throws Exception {
        final int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> tarefas = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String nome = "cli" + i;
            tarefas.add(pool.submit(() -> {
                largada.await();
                for (int volta = 0; volta < 30; volta++) {
                    DestinatarioFalso d = new DestinatarioFalso(nome);
                    assertTrue(sala.entrar(d));
                    sala.publicar(d, "volta " + volta);
                    assertTrue(sala.sair(d));
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> tarefa : tarefas) {
            tarefa.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(0, sala.totalConectados());
        assertEquals(threads * 30, repositorio.todas().size());
    }

    private DestinatarioFalso conectar(String nome) {
        DestinatarioFalso d = new DestinatarioFalso(nome);
        assertTrue(sala.entrar(d));
        return d;
    }
}
