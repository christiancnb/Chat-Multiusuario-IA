package br.unicesumar.chat.server;

import br.unicesumar.chat.agente.AgenteException;
import br.unicesumar.chat.agente.ServicoUci;
import br.unicesumar.chat.agente.Turno;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.GrupoRepositoryMemoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A UCI dentro da sala: quando é acionada, para quem responde e como falhas e limites aparecem. */
@Timeout(60)
class SalaChatUciTest {

    private RepositorioEmMemoria repositorio;
    private AgenteFalso agente;
    private ServicoUci uci;
    private SalaChat sala;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEmMemoria();
        agente = new AgenteFalso();
        uci = new ServicoUci(agente);
        sala = new SalaChat(repositorio, new GrupoRepositoryMemoria(), uci, 20);
    }

    @AfterEach
    void encerrar() {
        agente.liberar();
        uci.close();
    }

    // ---- conversa privada ----

    @Test
    void perguntaPrivadaRecebeRespostaPrivadaSoParaQuemPerguntou() {
        agente.responderCom("Olá, ana!");
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        Mensagem pergunta = sala.publicarPrivada(ana, "uci", "oi");

        assertEquals("ana", pergunta.getRemetente());
        assertEquals("UCI", pergunta.getDestino());
        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado): Olá, ana!")));
        assertFalse(bia.recebeuContendo("Olá, ana!"), "a conversa privada com a UCI é só de quem perguntou");
    }

    @Test
    void perguntaERespostaFicamGravadasComoMensagensPrivadas() {
        agente.responderCom("resposta gravada");
        DestinatarioFalso ana = conectar("ana");

        sala.publicarPrivada(ana, "UCI", "pergunta gravada");

        assertTrue(Espera.ate(() -> repositorio.todas().size() == 2));
        List<Mensagem> todas = repositorio.todas();
        assertEquals("ana", todas.get(0).getRemetente());
        assertEquals("UCI", todas.get(0).getDestino());
        assertEquals("UCI", todas.get(1).getRemetente());
        assertEquals("ana", todas.get(1).getDestino());
        assertEquals(Mensagem.Tipo.PRIVADA, todas.get(1).getTipo());
    }

    @Test
    void historicoPrivadoComAUciTrazPerguntaERespostaEmOrdem() {
        agente.responderCom("e aí!");
        DestinatarioFalso ana = conectar("ana");
        sala.publicarPrivada(ana, "UCI", "oi");
        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado): e aí!"))); // entrega ao vivo concluída
        ana.recebidas().clear();

        sala.enviarHistoricoPrivado(ana, "UCI");

        assertTrue(ana.recebidas().get(1).endsWith("ana -> UCI (privado): oi"));
        assertTrue(ana.recebidas().get(2).endsWith("UCI -> ana (privado): e aí!"));
    }

    @Test
    void aUciRecebeAPerguntaSemPrefixoEMantemOContextoDaConversa() {
        agente.responderCom("ok");
        DestinatarioFalso ana = conectar("ana");

        sala.publicarPrivada(ana, "UCI", "primeira");
        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado): ok")));
        perguntarQuandoLivre(ana, "segunda");
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 2));

        assertEquals("primeira", agente.perguntaDaChamada(0));
        List<Turno> segunda = agente.chamadas().get(1);
        assertEquals(3, segunda.size(), "pergunta anterior + resposta + pergunta nova");
        assertEquals("segunda", segunda.get(2).texto());
    }

    @Test
    void usuarioQueSaiEEntraDeNovoComOMesmoNomeNaoHerdaOContexto() {
        agente.responderCom("ok");
        DestinatarioFalso ana = conectar("ana");
        sala.publicarPrivada(ana, "UCI", "segredo da primeira sessão");
        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado): ok")));
        sala.sair(ana);

        DestinatarioFalso outraAna = conectar("ana");
        perguntarQuandoLivre(outraAna, "olá");
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 2));

        assertEquals(1, agente.chamadas().get(1).size(), "sem memória da sessão anterior");
    }

    // ---- menção @UCI ----

    @Test
    void mencaoNoChatGeralFazARespostaChegarATodos() {
        agente.responderCom("são 10h");
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        sala.publicar(ana, "ei @uci, que horas são?");

        assertTrue(Espera.ate(() -> bia.recebeuContendo("UCI: são 10h")));
        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI: são 10h")), "o autor também vê a resposta");
        assertEquals("ana: ei @uci, que horas são?", agente.perguntaDaChamada(0), "a IA sabe quem perguntou");
    }

    @Test
    void mencaoNoGrupoFazARespostaChegarSoAosMembros() {
        agente.responderCom("resposta ao grupo");
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");
        DestinatarioFalso caio = conectar("caio");
        sala.criarGrupo(ana, "estudo");
        sala.entrarNoGrupo(bia, "estudo");

        sala.publicarNoGrupo(ana, "estudo", "@UCI explique recursão");

        assertTrue(Espera.ate(() -> bia.recebeuContendo("#estudo UCI: resposta ao grupo")));
        assertTrue(Espera.ate(() -> ana.recebeuContendo("#estudo UCI: resposta ao grupo")));
        assertFalse(caio.recebeuContendo("resposta ao grupo"));
        assertTrue(repositorio.todas().stream().anyMatch(m -> m.getTipo() == Mensagem.Tipo.GRUPO
                && m.getRemetente().equals("UCI") && m.getDestino().equals("estudo")));
    }

    @Test
    void soAMencaoComoPalavraInteiraAciona() throws Exception {
        DestinatarioFalso ana = conectar("ana");
        conectar("bia");

        sala.publicar(ana, "escreva para ana@uci.com");
        sala.publicar(ana, "o @ucinho é outro bicho");
        sala.publicar(ana, "sem menção nenhuma");
        Thread.sleep(300);

        assertTrue(agente.chamadas().isEmpty());
    }

    @Test
    void mencaoNoFimOuEntrePontuacaoAciona() {
        DestinatarioFalso ana = conectar("ana");
        conectar("bia");

        sala.publicar(ana, "obrigada, @UCI!");

        assertTrue(Espera.ate(() -> agente.chamadas().size() == 1));
    }

    @Test
    void aRespostaDaUciNaoAcionaElaMesmaPorTerOTextoArroba() throws Exception {
        agente.responderCom("me chame de @UCI quando quiser");
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        sala.publicar(ana, "oi @uci");
        assertTrue(Espera.ate(() -> bia.recebeuContendo("UCI: me chame de @UCI")));
        Thread.sleep(300);

        assertEquals(1, agente.chamadas().size(), "sem laço infinito");
    }

    // ---- falhas e limites ----

    @Test
    void falhaDaIaVoltaComoErroAmigavelSoParaQuemPerguntou() {
        agente.falharCom(new AgenteException("A chave da API da UCI é inválida.", "HTTP 401"));
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        sala.publicarPrivada(ana, "UCI", "oi");

        assertTrue(Espera.ate(() -> ana.recebidas().contains("! A chave da API da UCI é inválida.")));
        assertFalse(ana.recebeuContendo("HTTP 401"), "o detalhe técnico fica só no log do servidor");
        assertFalse(bia.recebeuContendo("inválida"));
    }

    @Test
    void falhaDaIaNaMencaoDoChatGeralTambemAvisaSoOAutor() {
        agente.falharCom(new AgenteException("O serviço de IA está indisponível no momento.", "HTTP 503"));
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        sala.publicar(ana, "@UCI alguém aí?");

        assertTrue(Espera.ate(() -> ana.recebidas().contains("! O serviço de IA está indisponível no momento.")));
        assertFalse(bia.recebeuContendo("indisponível"));
    }

    @Test
    void segundaPerguntaEnquantoAPrimeiraRespondeEhRecusadaEDepoisLiberada() {
        agente.responderCom("demorei");
        agente.segurar();
        DestinatarioFalso ana = conectar("ana");

        sala.publicarPrivada(ana, "UCI", "primeira");
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 1));
        ChatException e = assertThrows(ChatException.class, () -> sala.publicarPrivada(ana, "UCI", "segunda"));
        assertTrue(e.getMessage().contains("ainda está respondendo"));

        agente.liberar();
        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado): demorei")));
        perguntarQuandoLivre(ana, "terceira"); // agora pode de novo
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 2));
    }

    @Test
    void usuariosDiferentesConversamComAUciAoMesmoTempo() {
        agente.responderCom("para todos");
        agente.segurar();
        DestinatarioFalso ana = conectar("ana");
        DestinatarioFalso bia = conectar("bia");

        sala.publicarPrivada(ana, "UCI", "oi da ana");
        sala.publicarPrivada(bia, "UCI", "oi da bia"); // conversa diferente: não é recusada
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 2));

        agente.liberar();
        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado)")));
        assertTrue(Espera.ate(() -> bia.recebeuContendo("UCI -> bia (privado)")));
    }

    @Test
    void respostaLongaViraVariasMensagensDeNoMaximo450Caracteres() {
        String palavra = "palavra ";
        agente.responderCom(palavra.repeat(150)); // ~1200 caracteres
        DestinatarioFalso ana = conectar("ana");

        sala.publicarPrivada(ana, "UCI", "conte uma história longa");

        assertTrue(Espera.ate(() -> repositorio.todas().size() >= 4));
        List<Mensagem> respostas = repositorio.todas().stream().filter(m -> m.getRemetente().equals("UCI")).toList();
        assertTrue(respostas.size() >= 3);
        for (Mensagem r : respostas) {
            assertTrue(r.getConteudo().length() <= ServicoUci.TAMANHO_PARTE + 1);
        }
    }

    @Test
    void respostaQueChegaDepoisDoUsuarioSairNaoQuebraNadaEFicaGravada() {
        agente.responderCom("tarde demais");
        agente.segurar();
        DestinatarioFalso ana = conectar("ana");
        conectar("bia");
        sala.publicarPrivada(ana, "UCI", "oi");
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 1));
        sala.sair(ana);

        agente.liberar();

        assertTrue(Espera.ate(() -> repositorio.todas().size() == 2), "a resposta é gravada para quando ela voltar");
        assertFalse(ana.recebeuContendo("tarde demais"));
        assertEquals(1, sala.totalConectados());
    }

    // ---- presença ----

    @Test
    void nomeUciEhReservadoEmQualquerCaixa() {
        assertFalse(sala.entrar(new DestinatarioFalso("UCI")));
        assertFalse(sala.entrar(new DestinatarioFalso("uci")));
        assertEquals(0, sala.totalConectados());
    }

    @Test
    void uciApareceNaListaDeOnlineSoQuandoEstaAtiva() {
        conectar("ana");
        assertEquals(List.of("ana", "UCI"), sala.listarUsuarios());
        assertEquals(List.of("ana"), sala.listarNomes(), "listarNomes só tem pessoas");

        SalaChat semUci = new SalaChat(new RepositorioEmMemoria(), 20);
        semUci.entrar(new DestinatarioFalso("ana"));
        assertEquals(List.of("ana"), semUci.listarUsuarios());
    }

    @Test
    void clienteGraficoVeAUciNaListaDeOnline() {
        DestinatarioFalso ana = conectar("ana");
        ana.ativarControle();

        conectar("bia");

        assertEquals("@@USUARIOS|ana,bia,UCI", ana.ultimoControle("@@USUARIOS|"));
    }

    /**
     * A UCI só libera "uma pergunta por vez" depois de entregar a resposta. Um teste que pergunta no
     * mesmo instante em que a resposta chega pode cair nessa janela de microssegundos (uma pessoa
     * nunca cai), então tenta de novo por até 2 s.
     */
    private void perguntarQuandoLivre(DestinatarioFalso quem, String texto) {
        long limite = System.nanoTime() + 2_000_000_000L;
        while (true) {
            try {
                sala.publicarPrivada(quem, "UCI", texto);
                return;
            } catch (ChatException e) {
                if (System.nanoTime() > limite || !e.getMessage().contains("ainda está respondendo")) {
                    throw e;
                }
                Espera.ate(() -> false, 10);
            }
        }
    }

    private DestinatarioFalso conectar(String nome) {
        DestinatarioFalso d = new DestinatarioFalso(nome);
        assertTrue(sala.entrar(d));
        return d;
    }
}
