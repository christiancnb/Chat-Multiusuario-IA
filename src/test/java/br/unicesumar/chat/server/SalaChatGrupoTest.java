package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Grupo;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.GrupoRepository;
import br.unicesumar.chat.persistence.GrupoRepositoryMemoria;
import br.unicesumar.chat.persistence.PersistenciaException;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grupos: criação, entrada/saída, entrega só aos membros, persistência e concorrência. */
@Timeout(30)
class SalaChatGrupoTest {

    private RepositorioEmMemoria repositorio;
    private GrupoRepositoryMemoria grupoRepositorio;
    private SalaChat sala;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEmMemoria();
        grupoRepositorio = new GrupoRepositoryMemoria();
        sala = new SalaChat(repositorio, grupoRepositorio, null, 20);
    }

    // ---- criação ----

    @Test
    void criadorViraPrimeiroMembroEONomeEhNormalizado() {
        DestinatarioFalso ana = conectar("ana");

        Grupo g = sala.criarGrupo(ana, "#Estudo");

        assertEquals("estudo", g.getNome());
        assertEquals(List.of("estudo"), sala.nomesDosGruposDe("ana"));
        assertEquals(List.of("ana"), sala.membrosDoGrupo("ESTUDO"));
        assertTrue(ana.recebidas().contains("* #estudo: Grupo criado. Você é o primeiro membro."));
    }

    @Test
    void grupoJaExistenteEhRecusadoSemDiferenciarMaiusculas() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        sala.criarGrupo(ana, "estudo");

        ChatException e = assertThrows(ChatException.class, () -> sala.criarGrupo(bia, "ESTUDO"));

        assertTrue(e.getMessage().contains("já existe"));
        assertEquals(List.of("ana"), sala.membrosDoGrupo("estudo"), "o grupo original não muda");
    }

    @Test
    void nomeDeGrupoInvalidoELancado() {
        DestinatarioFalso ana = conectar("ana");
        assertThrows(IllegalArgumentException.class, () -> sala.criarGrupo(ana, "a"));
        assertThrows(IllegalArgumentException.class, () -> sala.criarGrupo(ana, "meu grupo"));
        assertThrows(IllegalArgumentException.class, () -> sala.criarGrupo(ana, "   "));
        assertTrue(sala.nomesDosGrupos().isEmpty());
    }

    @Test
    void falhaAoGravarNoBancoNaoDeixaOGrupoSoNaMemoria() {
        GrupoRepository quebrado = new GrupoRepository() {
            @Override
            public List<Grupo> carregarTodos() {
                return List.of();
            }

            @Override
            public void criar(Grupo grupo) {
                throw new PersistenciaException("banco fora do ar (simulado)");
            }

            @Override
            public void adicionarMembro(String grupo, String usuario) {
                throw new PersistenciaException("banco fora do ar (simulado)");
            }

            @Override
            public void removerMembro(String grupo, String usuario) {
                throw new PersistenciaException("banco fora do ar (simulado)");
            }
        };
        SalaChat salaSemBanco = new SalaChat(repositorio, quebrado, null, 20);
        DestinatarioFalso ana = new DestinatarioFalso("ana");
        salaSemBanco.entrar(ana);

        ChatException e = assertThrows(ChatException.class, () -> salaSemBanco.criarGrupo(ana, "estudo"));

        assertTrue(e.getMessage().contains("falha ao gravar"));
        assertTrue(salaSemBanco.nomesDosGrupos().isEmpty(), "write-through: memória só muda se o banco confirmou");
    }

    // ---- mensagens de grupo ----

    @Test
    void mensagemDeGrupoChegaSoAosMembrosOnlineENaoAoRemetente() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        DestinatarioFalso caio = conectar("caio");
        sala.criarGrupo(ana, "estudo");
        sala.entrarNoGrupo(bia, "estudo");

        Mensagem m = sala.publicarNoGrupo(ana, "estudo", "prova amanhã");

        assertEquals(Mensagem.Tipo.GRUPO, m.getTipo());
        assertEquals("estudo", m.getDestino());
        assertTrue(bia.recebeuContendo("#estudo ana: prova amanhã"));
        assertFalse(caio.recebeuContendo("prova amanhã"), "quem não é membro não recebe");
        assertFalse(ana.recebeuContendo("prova amanhã"), "o remetente não recebe eco do servidor");
    }

    @Test
    void mensagemDeGrupoNaoApareceNoChatGeralNemNoHistoricoGeral() {
        DestinatarioFalso ana = conectar("ana");
        sala.criarGrupo(ana, "estudo");
        sala.publicarNoGrupo(ana, "estudo", "só do grupo");

        DestinatarioFalso caio = conectar("caio");

        assertFalse(caio.recebeuContendo("só do grupo"));
        assertEquals("--- Nenhuma mensagem anterior ---", caio.recebidas().get(0));
    }

    @Test
    void quemNaoEhMembroNaoPodeEnviar() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso caio = conectar("caio");
        sala.criarGrupo(ana, "estudo");

        ChatException e = assertThrows(ChatException.class, () -> sala.publicarNoGrupo(caio, "estudo", "furando"));

        assertTrue(e.getMessage().contains("não é membro"));
        assertTrue(repositorio.todas().isEmpty());
    }

    @Test
    void grupoInexistenteGeraErro() {
        DestinatarioFalso ana = conectar("ana");

        ChatException e = assertThrows(ChatException.class, () -> sala.publicarNoGrupo(ana, "nada", "oi"));

        assertTrue(e.getMessage().contains("não existe"));
    }

    @Test
    void conteudoInvalidoNoGrupoNaoEGravado() {
        DestinatarioFalso ana = conectar("ana");
        sala.criarGrupo(ana, "estudo");

        assertThrows(IllegalArgumentException.class, () -> sala.publicarNoGrupo(ana, "estudo", "  "));

        assertTrue(repositorio.todas().isEmpty());
    }

    // ---- entrar e sair ----

    @Test
    void quemEntraNoGrupoRecebeOHistoricoEOsMembrosSaoAvisados() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        sala.criarGrupo(ana, "estudo");
        sala.publicarNoGrupo(ana, "estudo", "primeira");
        sala.publicarNoGrupo(ana, "estudo", "segunda");
        bia.recebidas().clear();

        sala.entrarNoGrupo(bia, "estudo");

        List<String> r = bia.recebidas();
        assertEquals("* #estudo: Você entrou no grupo.", r.get(0));
        assertEquals("--- #estudo: últimas 2 mensagens ---", r.get(1));
        assertTrue(r.get(2).endsWith("#estudo ana: primeira"));
        assertTrue(r.get(3).endsWith("#estudo ana: segunda"));
        assertEquals("--- #estudo: fim do histórico ---", r.get(4));
        assertTrue(ana.recebidas().contains("* #estudo: bia entrou no grupo."));
    }

    @Test
    void entrarNoGrupoDoQualJaEMembroEErro() {
        DestinatarioFalso ana = conectar("ana");
        sala.criarGrupo(ana, "estudo");

        ChatException e = assertThrows(ChatException.class, () -> sala.entrarNoGrupo(ana, "estudo"));

        assertTrue(e.getMessage().contains("já é membro"));
    }

    @Test
    void quemSaiDoGrupoDeixaDeReceberEOsDemaisSaoAvisados() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        sala.criarGrupo(ana, "estudo");
        sala.entrarNoGrupo(bia, "estudo");

        sala.sairDoGrupo(bia, "estudo");
        sala.publicarNoGrupo(ana, "estudo", "depois que a bia saiu");

        assertTrue(ana.recebidas().contains("* #estudo: bia saiu do grupo."));
        assertTrue(bia.recebidas().contains("* #estudo: Você saiu do grupo."));
        assertFalse(bia.recebeuContendo("depois que a bia saiu"));
        assertTrue(sala.nomesDosGruposDe("bia").isEmpty());
        assertThrows(ChatException.class, () -> sala.publicarNoGrupo(bia, "estudo", "ainda posso?"));
    }

    @Test
    void grupoContinuaExistindoMesmoSemMembros() {
        DestinatarioFalso ana = conectar("ana");
        sala.criarGrupo(ana, "estudo");

        sala.sairDoGrupo(ana, "estudo");

        assertEquals(List.of("estudo"), sala.nomesDosGrupos());
        assertTrue(sala.membrosDoGrupo("estudo").isEmpty());
        sala.entrarNoGrupo(ana, "estudo"); // pode voltar
        assertEquals(List.of("ana"), sala.membrosDoGrupo("estudo"));
    }

    @Test
    void sairDeGrupoDoQualNaoEMembroEErro() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        sala.criarGrupo(ana, "estudo");

        assertThrows(ChatException.class, () -> sala.sairDoGrupo(bia, "estudo"));
    }

    // ---- persistência ----

    @Test
    void membroOfflineRecebeOHistoricoDoGrupoAoVoltar() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        sala.criarGrupo(ana, "estudo");
        sala.entrarNoGrupo(bia, "estudo");
        sala.sair(bia);
        sala.publicarNoGrupo(ana, "estudo", "enquanto você estava fora");

        DestinatarioFalso biaDeNovo = conectar("bia");

        assertTrue(biaDeNovo.recebidas().contains("--- #estudo: última mensagem ---"));
        assertTrue(biaDeNovo.recebeuContendo("#estudo ana: enquanto você estava fora"));
        assertEquals(List.of("estudo"), sala.nomesDosGruposDe("bia"), "a participação sobrevive à desconexão");
    }

    @Test
    void gruposSobrevivemAUmaNovaSalaComOMesmoBanco() {
        DestinatarioFalso ana = conectar("ana");
        sala.criarGrupo(ana, "estudo");
        sala.publicarNoGrupo(ana, "estudo", "antes do reinício");

        SalaChat salaNova = new SalaChat(repositorio, grupoRepositorio, null, 20);
        DestinatarioFalso anaDeNovo = new DestinatarioFalso("ana");
        assertTrue(salaNova.entrar(anaDeNovo));

        assertEquals(List.of("estudo"), salaNova.nomesDosGrupos());
        assertEquals(List.of("ana"), salaNova.membrosDoGrupo("estudo"));
        assertTrue(anaDeNovo.recebeuContendo("#estudo ana: antes do reinício"));
    }

    @Test
    void historicoDoGrupoSoParaMembros() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso caio = conectar("caio");
        sala.criarGrupo(ana, "estudo");
        sala.publicarNoGrupo(ana, "estudo", "conteúdo reservado");
        ana.recebidas().clear();

        sala.enviarHistoricoDoGrupo(ana, "estudo");
        assertTrue(ana.recebeuContendo("conteúdo reservado"));
        assertThrows(ChatException.class, () -> sala.enviarHistoricoDoGrupo(caio, "estudo"));
        assertFalse(caio.recebeuContendo("conteúdo reservado"));
    }

    // ---- linhas de controle (clientes gráficos) ----

    @Test
    void soClientesGraficosRecebemLinhasDeControle() {
        DestinatarioFalso grafico = conectar("ana");
        grafico.ativarControle();
        DestinatarioFalso console = conectar("bia");

        sala.criarGrupo(grafico, "estudo");

        assertEquals("@@USUARIOS|ana,bia", grafico.ultimoControle("@@USUARIOS|"));
        assertEquals("@@GRUPOS|estudo|estudo", grafico.ultimoControle("@@GRUPOS|"));
        assertTrue(console.controle().isEmpty(), "o console nunca vê linhas @@");
    }

    @Test
    void listaDeOnlineAtualizaQuandoAlguemEntraOuSai() {
        DestinatarioFalso ana = conectar("ana");
        ana.ativarControle();

        DestinatarioFalso bia = conectar("bia");
        assertEquals("@@USUARIOS|ana,bia", ana.ultimoControle("@@USUARIOS|"));

        sala.sair(bia);
        assertEquals("@@USUARIOS|ana", ana.ultimoControle("@@USUARIOS|"));
    }

    @Test
    void cadaClienteGraficoRecebeSeusProprioGruposETodosOsGrupos() {
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        ana.ativarControle();
        bia.ativarControle();

        sala.criarGrupo(ana, "estudo");
        sala.criarGrupo(bia, "time");

        assertEquals("@@GRUPOS|estudo|estudo,time", ana.ultimoControle("@@GRUPOS|"));
        assertEquals("@@GRUPOS|time|estudo,time", bia.ultimoControle("@@GRUPOS|"));
    }

    @Test
    void enviarEstadoMandaOnlineEGruposDeUmCliente() {
        DestinatarioFalso ana = conectar("ana");
        sala.criarGrupo(ana, "estudo");
        ana.ativarControle();

        sala.enviarEstado(ana);

        assertEquals("@@USUARIOS|ana", ana.ultimoControle("@@USUARIOS|"));
        assertEquals("@@GRUPOS|estudo|estudo", ana.ultimoControle("@@GRUPOS|"));
        assertNull(conectar("bia").ultimoControle("@@USUARIOS|"));
    }

    // ---- concorrência ----

    @Test
    void criacaoSimultaneaDoMesmoGrupoTemUmUnicoVencedor() throws Exception {
        final int tentativas = 8;
        List<DestinatarioFalso> usuarios = new ArrayList<>();
        for (int i = 0; i < tentativas; i++) {
            usuarios.add(conectar("user" + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger vencedores = new AtomicInteger();
        AtomicInteger recusas = new AtomicInteger();
        List<Future<?>> tarefas = new ArrayList<>();
        for (DestinatarioFalso u : usuarios) {
            tarefas.add(pool.submit(() -> {
                largada.await();
                try {
                    sala.criarGrupo(u, "disputa");
                    vencedores.incrementAndGet();
                } catch (ChatException e) {
                    recusas.incrementAndGet();
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> t : tarefas) {
            t.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, vencedores.get());
        assertEquals(tentativas - 1, recusas.get());
        assertEquals(List.of("disputa"), sala.nomesDosGrupos());
        assertEquals(1, sala.membrosDoGrupo("disputa").size());
    }

    @Test
    void mensagensConcorrentesNoGrupoNaoSePerdemEChegamNaMesmaOrdem() throws Exception {
        final int autoresQtd = 6;
        final int porAutor = 40;
        DestinatarioFalso dono = conectar("dono");
        sala.criarGrupo(dono, "pista");
        DestinatarioFalso obs1 = conectar("obs1");
        DestinatarioFalso obs2 = conectar("obs2");
        sala.entrarNoGrupo(obs1, "pista");
        sala.entrarNoGrupo(obs2, "pista");
        List<DestinatarioFalso> autores = new ArrayList<>();
        for (int i = 0; i < autoresQtd; i++) {
            DestinatarioFalso a = conectar("autor" + i);
            sala.entrarNoGrupo(a, "pista");
            autores.add(a);
        }

        ExecutorService pool = Executors.newFixedThreadPool(autoresQtd);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> tarefas = new ArrayList<>();
        for (DestinatarioFalso autor : autores) {
            tarefas.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < porAutor; i++) {
                    sala.publicarNoGrupo(autor, "pista", "n" + i);
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> t : tarefas) {
            t.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(autoresQtd * porAutor, repositorio.todas().size(), "todas gravadas");
        assertEquals(autoresQtd * porAutor, obs1.mensagens().size(), "nenhuma se perde no broadcast do grupo");
        assertEquals(obs1.mensagens(), obs2.mensagens(), "mesma ordem para todos os membros");
    }

    private DestinatarioFalso conectar(String nome) {
        DestinatarioFalso d = new DestinatarioFalso(nome);
        assertTrue(sala.entrar(d));
        return d;
    }
}
