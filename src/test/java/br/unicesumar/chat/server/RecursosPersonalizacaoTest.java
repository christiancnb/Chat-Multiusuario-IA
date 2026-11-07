package br.unicesumar.chat.server;

import br.unicesumar.chat.agente.ServicoUci;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.GrupoRepositoryMemoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Status, "digitando", busca, moderação, estilo/utilidades da UCI e administração de grupo (sem rede). */
class RecursosPersonalizacaoTest {

    private RepositorioEmMemoria repositorio;
    private AgenteFalso agente;
    private ServicoUci uci;
    private SalaChat sala;
    private ComandosChat comandos;
    private DestinatarioFalso ana;
    private DestinatarioFalso bia;
    private DestinatarioFalso caio;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEmMemoria();
        agente = new AgenteFalso();
        uci = new ServicoUci(agente);
        sala = new SalaChat(repositorio, new GrupoRepositoryMemoria(), uci, 20);
        comandos = new ComandosChat(sala);
        ana = conectar("ana", true);
        bia = conectar("bia", true);
        caio = conectar("caio", false); // cliente de terminal: nunca recebe linhas de controle
        limpar();
    }

    @AfterEach
    void encerrar() {
        agente.liberar();
        uci.close();
    }

    private DestinatarioFalso conectar(String nome, boolean grafico) {
        DestinatarioFalso d = new DestinatarioFalso(nome);
        sala.entrar(d);
        if (grafico) {
            d.ativarControle();
        }
        return d;
    }

    private void limpar() {
        ana.recebidas().clear();
        bia.recebidas().clear();
        caio.recebidas().clear();
    }

    // ---- status ----

    @Test
    void statusMudaEEhAvisadoSoAosClientesGraficos() {
        comandos.executar(ana, "/status ocupado");

        assertTrue(ana.recebeuContendo("Status alterado para ocupado."));
        assertEquals("@@STATUS|ana=ocupado", bia.ultimoControle("@@STATUS|"));
        assertTrue(caio.controle().isEmpty(), "terminal nunca vê linhas de controle");
    }

    @Test
    void usuariosMostraOStatusDeQuemNaoEstaDisponivel() {
        comandos.executar(bia, "/status Ausente");
        limpar();

        comandos.executar(caio, "/usuarios");

        assertEquals("Conectados: ana, bia (ausente), caio, UCI", caio.recebidas().get(0));
    }

    @Test
    void voltarParaDisponivelLimpaOStatus() {
        comandos.executar(ana, "/status ocupado");
        comandos.executar(ana, "/status disponivel");

        assertEquals("@@STATUS|", bia.ultimoControle("@@STATUS|"));
    }

    @Test
    void statusInvalidoInformaOUso() {
        comandos.executar(ana, "/status dormindo");

        assertTrue(ana.recebidas().get(0).startsWith("! Uso: /status"));
    }

    @Test
    void statusSemArgumentoMostraOAtual() {
        comandos.executar(ana, "/status");

        assertTrue(ana.recebidas().get(0).startsWith("Seu status: disponivel"));
    }

    @Test
    void statusSomeQuandoOUsuarioSai() {
        comandos.executar(ana, "/status ocupado");
        limpar();

        sala.sair(ana);

        assertEquals("@@STATUS|", bia.ultimoControle("@@STATUS|"));
        assertEquals(br.unicesumar.chat.model.StatusUsuario.DISPONIVEL, sala.statusDe("ana"));
    }

    @Test
    void novoClienteGraficoRecebeOsStatusAoPedirOEstado() {
        comandos.executar(ana, "/status ocupado");
        limpar();

        comandos.executar(bia, "/modo gui");

        assertEquals("@@STATUS|ana=ocupado", bia.ultimoControle("@@STATUS|"));
    }

    // ---- digitando ----

    @Test
    void digitandoNoGeralChegaAosOutrosClientesGraficos() {
        comandos.executar(ana, "/digitando geral");

        assertEquals("@@DIGITANDO|ana|geral", bia.ultimoControle("@@DIGITANDO|"));
        assertNull(ana.ultimoControle("@@DIGITANDO|"), "quem digita não recebe o próprio aviso");
        assertTrue(caio.recebidas().isEmpty());
    }

    @Test
    void digitandoNoPrivadoChegaSoAoDestinatarioComONomeDeQuemDigita() {
        comandos.executar(ana, "/digitando bia");

        assertEquals("@@DIGITANDO|ana|ana", bia.ultimoControle("@@DIGITANDO|"));
        assertTrue(ana.recebidas().isEmpty());
    }

    @Test
    void digitandoNoGrupoSoChegaAosMembros() {
        DestinatarioFalso dani = conectar("dani", true);
        comandos.executar(ana, "/grupo criar estudo");
        comandos.executar(bia, "/grupo entrar estudo");
        dani.recebidas().clear();
        bia.recebidas().clear();

        comandos.executar(ana, "/digitando #estudo");

        assertEquals("@@DIGITANDO|ana|#estudo", bia.ultimoControle("@@DIGITANDO|"));
        assertNull(dani.ultimoControle("@@DIGITANDO|"));
    }

    @Test
    void digitandoEmGrupoInexistenteOuSemSerMembroEhIgnoradoEmSilencio() {
        comandos.executar(ana, "/grupo criar secreto");
        limpar();

        comandos.executar(bia, "/digitando #secreto");
        comandos.executar(bia, "/digitando #naoexiste");

        assertTrue(bia.recebidas().isEmpty(), "nenhum erro aparece para um aviso de digitação");
        assertNull(ana.ultimoControle("@@DIGITANDO|"));
    }

    // ---- busca ----

    @Test
    void buscarAcheMensagensDaSalaGeralEIgnoraMaiusculas() {
        comandos.executar(ana, "Reunião amanhã às 10h");
        comandos.executar(bia, "bom dia");
        limpar();

        comandos.executar(caio, "/buscar REUNIÃO");

        assertTrue(caio.recebidas().get(0).startsWith("Busca por \"REUNIÃO\": 1 resultado"));
        assertTrue(caio.recebidas().get(1).startsWith("  > [") && caio.recebidas().get(1).contains("ana: Reunião amanhã"));
    }

    @Test
    void buscarNaoMostraPrivadosNemGruposAlheios() {
        comandos.executar(ana, "/msg bia segredo do projeto");
        comandos.executar(ana, "/grupo criar time");
        comandos.executar(ana, "/g time segredo no grupo");
        limpar();

        comandos.executar(caio, "/buscar segredo");
        comandos.executar(bia, "/buscar segredo");

        assertEquals("Busca por \"segredo\": nenhum resultado.", caio.recebidas().get(0));
        assertTrue(bia.recebidas().get(0).contains("1 resultado"), "bia só vê o privado dela, não o grupo");
        assertTrue(bia.recebeuContendo("segredo do projeto"));
        assertFalse(bia.recebeuContendo("segredo no grupo"));
    }

    @Test
    void buscarComTermoCurtoOuVazioInformaOUso() {
        comandos.executar(ana, "/buscar a");
        comandos.executar(ana, "/buscar");

        assertEquals(2, ana.recebidas().size());
        assertTrue(ana.recebidas().get(0).startsWith("! Uso: /buscar"));
        assertTrue(ana.recebidas().get(1).startsWith("! Uso: /buscar"));
    }

    @Test
    void buscarLimitaAosResultadosMaisRecentes() {
        for (int i = 1; i <= SalaChat.LIMITE_BUSCA + 5; i++) {
            comandos.executar(ana, "item numero " + i);
        }
        limpar();

        comandos.executar(bia, "/buscar item numero");

        assertEquals(1 + SalaChat.LIMITE_BUSCA, bia.recebidas().size());
        assertTrue(bia.recebidas().get(bia.recebidas().size() - 1).endsWith("item numero " + (SalaChat.LIMITE_BUSCA + 5)));
    }

    // ---- moderação ----

    @Test
    void moderadorTrocaPalavrasOfensivasEAvisaOAutor() {
        sala.usarModerador(ModeradorPorLista.padrao());

        comandos.executar(ana, "você é um IDIOTA mesmo");

        assertTrue(bia.recebeuContendo("ana: você é um ****** mesmo"));
        assertTrue(ana.recebeuContendo("! Sua mensagem tinha palavras ofensivas"));
        assertEquals("você é um ****** mesmo", repositorio.todas().get(0).getConteudo());
    }

    @Test
    void moderadorTambemVale_noPrivadoENoGrupo() {
        sala.usarModerador(ModeradorPorLista.padrao());
        comandos.executar(ana, "/grupo criar time");

        comandos.executar(ana, "/msg bia seu babaca");
        comandos.executar(ana, "/g time otário!");

        assertTrue(bia.recebeuContendo("ana -> bia (privado): seu ******"));
        assertTrue(repositorio.todas().stream().anyMatch(m -> m.getTipo() == Mensagem.Tipo.GRUPO
                && m.getConteudo().equals("******!")));
    }

    @Test
    void semModeradorNadaMuda() {
        comandos.executar(ana, "idiota");

        assertTrue(bia.recebeuContendo("ana: idiota"));
    }

    // ---- estilo e utilidades da UCI ----

    @Test
    void modoDaUciEhPorUsuarioEVaiJuntoDaPergunta() {
        agente.responderCom("ok");

        comandos.executar(ana, "/uci modo formal");
        comandos.executar(ana, "/msg UCI que horas são?");

        assertTrue(ana.recebeuContendo("A UCI agora responde no modo formal para você."));
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 1));
        assertTrue(agente.perguntaDaChamada(0).startsWith("[[Responda de forma formal"));
        assertTrue(agente.perguntaDaChamada(0).endsWith("que horas são?"));

        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado): ok")));
        comandos.executar(bia, "/msg UCI e agora?");
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 2));
        assertEquals("e agora?", agente.perguntaDaChamada(1), "o modo de ana não vale para bia");
    }

    @Test
    void instrucaoDeEstiloNaoFicaNaMemoriaDaConversa() {
        agente.responderCom("ok");
        comandos.executar(ana, "/uci modo professora");

        comandos.executar(ana, "/msg UCI primeira");
        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado): ok")));
        ana.recebidas().clear();
        comandos.executar(ana, "/msg UCI segunda");
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 2));

        var segunda = agente.chamadas().get(1);
        assertEquals("primeira", segunda.get(0).texto(), "a memória guarda só o que o usuário escreveu");
        assertTrue(segunda.get(2).texto().startsWith("[[") && segunda.get(2).texto().endsWith("segunda"));
    }

    @Test
    void modoInvalidoEModoAtual() {
        comandos.executar(ana, "/uci modo pirata");
        comandos.executar(ana, "/uci modo");

        assertTrue(ana.recebidas().get(0).startsWith("! Modo desconhecido 'pirata'"));
        assertTrue(ana.recebidas().get(1).startsWith("Modo da UCI para você: padrao"));
    }

    @Test
    void modoVoltaAoPadraoQuandoOUsuarioSai() {
        comandos.executar(ana, "/uci modo formal");
        sala.sair(ana);

        assertEquals("padrao", sala.personalidadeDe("ana").nome());
    }

    @Test
    void resumirEnviaAsMensagensDaConversaERespondeSoAQuemPediu() {
        agente.responderCom("Resumo pronto.");
        comandos.executar(ana, "vamos marcar a prova");
        comandos.executar(bia, "pode ser sexta");
        limpar();

        comandos.executar(caio, "/uci resumir");

        assertTrue(Espera.ate(() -> caio.recebeuContendo("UCI -> caio (privado): Resumo pronto.")));
        String pedido = agente.perguntaDaChamada(0);
        assertTrue(pedido.contains("ana: vamos marcar a prova") && pedido.contains("bia: pode ser sexta"));
        assertFalse(ana.recebeuContendo("Resumo pronto."));
    }

    @Test
    void resumirGrupoExigeSerMembro() {
        comandos.executar(ana, "/grupo criar time");
        comandos.executar(ana, "/g time oi time");
        limpar();

        comandos.executar(bia, "/uci resumir #time");

        assertTrue(bia.recebidas().get(0).startsWith("! Você não é membro do grupo #time"));
        assertTrue(agente.chamadas().isEmpty());
    }

    @Test
    void resumirSemMensagensInformaOMotivo() {
        comandos.executar(ana, "/uci resumir");

        assertEquals("! Não há mensagens para resumir nessa conversa.", ana.recebidas().get(0));
    }

    @Test
    void traduzirEExplicarMontamOPedidoParaAUci() {
        comandos.executar(ana, "/uci traduzir inglês bom dia, tudo bem?");
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 1));
        assertEquals("Traduza para inglês, respondendo só com a tradução: bom dia, tudo bem?", agente.perguntaDaChamada(0));

        assertTrue(Espera.ate(() -> ana.recebeuContendo("UCI -> ana (privado)")));
        comandos.executar(ana, "/uci explicar o que é um socket");
        assertTrue(Espera.ate(() -> agente.chamadas().size() == 2));
        assertEquals("Explique de forma simples e curta: o que é um socket", agente.perguntaDaChamada(1));
    }

    @Test
    void usoIncorretoDosSubcomandosDaUci() {
        comandos.executar(ana, "/uci");
        comandos.executar(ana, "/uci traduzir inglês");
        comandos.executar(ana, "/uci explicar");
        comandos.executar(ana, "/uci dancar");

        assertEquals(4, ana.recebidas().size());
        assertTrue(ana.recebidas().stream().allMatch(l -> l.startsWith("! Uso:")));
    }

    @Test
    void utilidadesSemUciAtivaInformamOMotivo() {
        SalaChat semUci = new SalaChat(new RepositorioEmMemoria(), 20);
        ComandosChat cmd = new ComandosChat(semUci);
        DestinatarioFalso zeca = new DestinatarioFalso("zeca");
        semUci.entrar(zeca);
        cmd.executar(zeca, "x");

        cmd.executar(zeca, "/uci explicar socket");

        assertEquals("! A UCI não está disponível neste servidor.", zeca.recebidas().get(zeca.recebidas().size() - 1));
    }

    // ---- administração de grupo ----

    @Test
    void criadorRemoveMembroEOsOutrosSaoAvisados() {
        comandos.executar(ana, "/grupo criar time");
        comandos.executar(bia, "/grupo entrar time");
        comandos.executar(caio, "/grupo entrar time");
        limpar();

        comandos.executar(ana, "/grupo remover time bia");

        assertTrue(bia.recebeuContendo("Você foi removido do grupo por ana."));
        assertTrue(caio.recebeuContendo("bia foi removido do grupo por ana."));
        assertEquals(java.util.List.of("ana", "caio"), sala.membrosDoGrupo("time"));
        assertTrue(bia.ultimoControle("@@GRUPOS|").startsWith("@@GRUPOS||"), "bia deixa de listar o grupo como dela");
    }

    @Test
    void soOCriadorPodeRemover() {
        comandos.executar(ana, "/grupo criar time");
        comandos.executar(bia, "/grupo entrar time");
        comandos.executar(caio, "/grupo entrar time");
        limpar();

        comandos.executar(bia, "/grupo remover time caio");

        assertEquals("! Só o criador do grupo #time pode remover membros.", bia.recebidas().get(0));
        assertTrue(sala.membrosDoGrupo("time").contains("caio"));
    }

    @Test
    void naoRemoveASiMesmoNemQuemNaoEhMembroNemFaltandoArgumento() {
        comandos.executar(ana, "/grupo criar time");
        limpar();

        comandos.executar(ana, "/grupo remover time ana");
        comandos.executar(ana, "/grupo remover time bia");
        comandos.executar(ana, "/grupo remover time");

        assertTrue(ana.recebidas().get(0).startsWith("! Você é o criador do grupo."));
        assertEquals("! 'bia' não é membro do grupo #time.", ana.recebidas().get(1));
        assertEquals("! Uso: /grupo remover <grupo> <usuario>", ana.recebidas().get(2));
    }

    @Test
    void exportarNoServidorExplicaQueEhDoCliente() {
        comandos.executar(ana, "/exportar");

        assertTrue(ana.recebidas().get(0).startsWith("! O /exportar funciona no cliente de terminal"));
    }
}
