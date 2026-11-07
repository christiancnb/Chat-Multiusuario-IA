package br.unicesumar.chat.server;

import br.unicesumar.chat.server.ComandosChat.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Interpretação do que o usuário digita (texto comum e comandos), sem rede: só objetos de mentira. */
class ComandosChatTest {

    private RepositorioEmMemoria repositorio;
    private SalaChat sala;
    private ComandosChat comandos;
    private DestinatarioFalso ana;
    private DestinatarioFalso bia;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEmMemoria();
        sala = new SalaChat(repositorio, 20);
        comandos = new ComandosChat(sala);
        ana = conectar("ana");
        bia = conectar("bia");
        ana.recebidas().clear();
        bia.recebidas().clear();
    }

    // ---- texto comum e comandos básicos ----

    @Test
    void textoSemBarraViraMensagemNoChatGeral() {
        assertEquals(Resultado.CONTINUAR, comandos.executar(ana, "  olá, pessoal  "));

        assertTrue(bia.recebeuContendo("ana: olá, pessoal"));
        assertEquals(1, repositorio.todas().size());
    }

    @Test
    void linhaVaziaOuNulaNaoFazNada() {
        assertEquals(Resultado.CONTINUAR, comandos.executar(ana, "   "));
        assertEquals(Resultado.CONTINUAR, comandos.executar(ana, null));
        assertTrue(repositorio.todas().isEmpty());
        assertTrue(ana.recebidas().isEmpty());
    }

    @Test
    void mensagemInvalidaDevolveErroSoAoRemetente() {
        comandos.executar(ana, "x".repeat(501));

        assertTrue(ana.recebidas().get(0).startsWith("! A mensagem excede o limite"));
        assertTrue(bia.recebidas().isEmpty());
    }

    @Test
    void sairDespedeESinalizaOFimDaConexao() {
        assertEquals(Resultado.SAIR, comandos.executar(ana, "/sair"));
        assertEquals(Resultado.SAIR, comandos.executar(bia, "/SAIR"));
        assertTrue(ana.recebidas().contains("Até logo!"));
    }

    @Test
    void usuariosListaOsConectados() {
        comandos.executar(ana, "/usuarios");
        assertEquals(List.of("Conectados: ana, bia"), ana.recebidas());
    }

    @Test
    void ajudaListaOsComandosEMencionaAUci() {
        comandos.executar(ana, "/ajuda");

        assertTrue(ana.recebeuContendo("/msg <usuario> <texto>"));
        assertTrue(ana.recebeuContendo("/grupo criar <nome>"));
        assertTrue(ana.recebeuContendo("@UCI"));
    }

    @Test
    void comandoDesconhecidoEhInformadoSemDerrubarNada() {
        assertEquals(Resultado.CONTINUAR, comandos.executar(ana, "/dancar"));
        assertEquals("! Comando desconhecido: /dancar. Digite /ajuda.", ana.recebidas().get(0));
    }

    @Test
    void comandosNaoDiferenciamMaiusculas() {
        comandos.executar(ana, "/USUARIOS");
        assertEquals("Conectados: ana, bia", ana.recebidas().get(0));
    }

    // ---- privado ----

    @Test
    void msgEnviaMensagemPrivada() {
        comandos.executar(ana, "/msg bia oi, tudo bem?");

        assertTrue(bia.recebeuContendo("ana -> bia (privado): oi, tudo bem?"));
        assertTrue(ana.recebidas().isEmpty());
    }

    @Test
    void apelidosDeMsgFuncionam() {
        comandos.executar(ana, "/p bia um");
        comandos.executar(ana, "/privado bia dois");

        assertEquals(2, bia.mensagens().size());
    }

    @Test
    void msgSemTextoMostraOUso() {
        comandos.executar(ana, "/msg bia");
        comandos.executar(ana, "/msg");

        assertEquals("! Uso: /msg <usuario> <texto>", ana.recebidas().get(0));
        assertEquals("! Uso: /msg <usuario> <texto>", ana.recebidas().get(1));
    }

    @Test
    void msgParaUsuarioOfflineMostraOErro() {
        comandos.executar(ana, "/msg fantasma oi");
        assertEquals("! O usuário 'fantasma' não está online.", ana.recebidas().get(0));
    }

    @Test
    void msgParaUciSemUciAtivaMostraOErro() {
        comandos.executar(ana, "/msg UCI oi");
        assertTrue(ana.recebidas().get(0).startsWith("! A UCI não está disponível"));
    }

    // ---- grupos ----

    @Test
    void fluxoCompletoDeGrupoPeloComando() {
        comandos.executar(ana, "/grupo criar estudo");
        comandos.executar(bia, "/grupo entrar estudo");
        comandos.executar(ana, "/g estudo bom dia, grupo");

        assertTrue(bia.recebeuContendo("#estudo ana: bom dia, grupo"));

        comandos.executar(bia, "/grupo sair estudo");
        comandos.executar(ana, "/g estudo e agora?");
        assertFalse(bia.recebeuContendo("e agora?"));
    }

    @Test
    void grupoComandoAceitaOHashENomeEmMaiusculas() {
        comandos.executar(ana, "/grupo criar #Estudo");
        comandos.executar(bia, "/GRUPO ENTRAR #ESTUDO");

        assertEquals(List.of("estudo"), sala.nomesDosGruposDe("bia"));
    }

    @Test
    void grupoMembrosListaOsMembros() {
        comandos.executar(ana, "/grupo criar estudo");
        comandos.executar(bia, "/grupo entrar estudo");
        ana.recebidas().clear();

        comandos.executar(ana, "/grupo membros estudo");

        assertEquals("* #estudo: Membros: ana, bia", ana.recebidas().get(0));
    }

    @Test
    void gruposListaOsMeusEOsOutros() {
        comandos.executar(ana, "/grupo criar estudo");
        comandos.executar(bia, "/grupo criar time");
        ana.recebidas().clear();

        comandos.executar(ana, "/grupos");

        assertEquals("Seus grupos: estudo", ana.recebidas().get(0));
        assertEquals("Outros grupos: time", ana.recebidas().get(1));
    }

    @Test
    void gruposSemNenhumMostraNenhum() {
        comandos.executar(ana, "/grupos");
        assertEquals("Seus grupos: (nenhum)", ana.recebidas().get(0));
        assertEquals("Outros grupos: (nenhum)", ana.recebidas().get(1));
    }

    @Test
    void errosDeGrupoVoltamComoMensagemAoUsuario() {
        comandos.executar(ana, "/grupo criar a");              // nome inválido
        comandos.executar(ana, "/grupo entrar naoexiste");     // grupo inexistente
        comandos.executar(ana, "/g naoexiste oi");             // idem
        comandos.executar(ana, "/grupo voar estudo");          // subcomando inválido
        comandos.executar(ana, "/grupo criar");                // faltou o nome
        comandos.executar(ana, "/g estudo");                   // faltou o texto

        List<String> r = ana.recebidas();
        assertTrue(r.get(0).startsWith("! Nome de grupo inválido"));
        assertTrue(r.get(1).contains("não existe"));
        assertTrue(r.get(2).contains("não existe"));
        assertEquals("! Uso: /grupo criar|entrar|sair|membros <nome>", r.get(3));
        assertEquals("! Uso: /grupo criar|entrar|sair|membros <nome>", r.get(4));
        assertEquals("! Uso: /g <grupo> <texto>", r.get(5));
    }

    // ---- histórico ----

    @Test
    void historicoPrivadoEDeGrupoPeloComando() {
        comandos.executar(ana, "/msg bia oi");
        comandos.executar(ana, "/grupo criar estudo");
        comandos.executar(ana, "/g estudo prova");
        ana.recebidas().clear();

        comandos.executar(ana, "/historico bia");
        comandos.executar(ana, "/historico #estudo");

        assertTrue(ana.recebeuContendo("ana -> bia (privado): oi"));
        assertTrue(ana.recebeuContendo("#estudo ana: prova"));
    }

    @Test
    void historicoSemArgumentoMostraOUso() {
        comandos.executar(ana, "/historico");
        assertEquals("! Uso: /historico <usuario|#grupo>", ana.recebidas().get(0));
    }

    // ---- modo gráfico ----

    @Test
    void modoGuiAtivaAsLinhasDeControleEEnviaOEstadoAtual() {
        comandos.executar(ana, "/grupo criar estudo");
        assertTrue(ana.controle().isEmpty(), "console não recebe controle");

        comandos.executar(ana, "/modo gui");

        assertTrue(ana.aceitaControle());
        assertEquals("@@USUARIOS|ana,bia", ana.ultimoControle("@@USUARIOS|"));
        assertEquals("@@GRUPOS|estudo|estudo", ana.ultimoControle("@@GRUPOS|"));
        assertFalse(bia.aceitaControle());
    }

    @Test
    void modoComArgumentoInvalidoMostraOUso() {
        comandos.executar(ana, "/modo xyz");
        assertEquals("! Uso: /modo gui", ana.recebidas().get(0));
        assertFalse(ana.aceitaControle());
    }

    private DestinatarioFalso conectar(String nome) {
        DestinatarioFalso d = new DestinatarioFalso(nome);
        assertTrue(sala.entrar(d));
        return d;
    }
}
