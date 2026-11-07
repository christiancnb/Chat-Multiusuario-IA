package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mensagens gerais, de grupo e privadas no mesmo banco: separação por tipo e migração de bancos antigos. */
class JdbcMensagemTiposTest {

    private ConexaoFactory fabrica;
    private JdbcMensagemRepository repositorio;

    @BeforeEach
    void criarBanco() {
        fabrica = BancoDeTeste.novaFabrica();
        repositorio = new JdbcMensagemRepository(fabrica);
        repositorio.criarEsquema();
    }

    @Test
    void salvarPreservaTipoEDestino() {
        Mensagem g = repositorio.salvar(Mensagem.grupo("estudo", "ana", "no grupo"));
        Mensagem p = repositorio.salvar(Mensagem.privada("bia", "ana", "no privado"));
        Mensagem c = repositorio.salvar(new Mensagem("ana", "no geral"));

        assertEquals(Mensagem.Tipo.GRUPO, repositorio.buscarUltimasDoGrupo("estudo", 5).get(0).getTipo());
        assertEquals("estudo", repositorio.buscarUltimasDoGrupo("estudo", 5).get(0).getDestino());
        assertEquals(Mensagem.Tipo.PRIVADA, repositorio.buscarUltimasPrivadas("ana", "bia", 5).get(0).getTipo());
        assertEquals("bia", repositorio.buscarUltimasPrivadas("ana", "bia", 5).get(0).getDestino());
        assertEquals(Mensagem.Tipo.GERAL, repositorio.buscarUltimas(5).get(0).getTipo());
        assertTrue(g.getId() != null && p.getId() != null && c.getId() != null);
    }

    @Test
    void mensagemGeralNaoTemDestino() {
        repositorio.salvar(new Mensagem("ana", "oi"));
        assertNull(repositorio.buscarUltimas(1).get(0).getDestino());
    }

    @Test
    void historicoGeralNaoMisturaGrupoNemPrivado() {
        repositorio.salvar(new Mensagem("ana", "geral 1"));
        repositorio.salvar(Mensagem.grupo("estudo", "ana", "grupo"));
        repositorio.salvar(Mensagem.privada("bia", "ana", "privado"));
        repositorio.salvar(new Mensagem("ana", "geral 2"));

        List<Mensagem> gerais = repositorio.buscarUltimas(10);

        assertEquals(List.of("geral 1", "geral 2"), gerais.stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void historicoDeGrupoFiltraPeloGrupoEOrdenaCronologicamente() {
        repositorio.salvar(Mensagem.grupo("estudo", "ana", "e1"));
        repositorio.salvar(Mensagem.grupo("time", "bia", "t1"));
        repositorio.salvar(Mensagem.grupo("estudo", "bia", "e2"));
        repositorio.salvar(Mensagem.grupo("estudo", "ana", "e3"));

        List<Mensagem> ultimas = repositorio.buscarUltimasDoGrupo("estudo", 2);

        assertEquals(List.of("e2", "e3"), ultimas.stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void historicoPrivadoVaiNosDoisSentidosSemDiferenciarMaiusculasEIgnoraOutrasConversas() {
        repositorio.salvar(Mensagem.privada("bia", "ana", "1 ana->bia"));
        repositorio.salvar(Mensagem.privada("ana", "bia", "2 bia->ana"));
        repositorio.salvar(Mensagem.privada("caio", "ana", "outra conversa"));
        repositorio.salvar(Mensagem.privada("BIA", "Ana", "3 Ana->BIA"));

        List<Mensagem> a = repositorio.buscarUltimasPrivadas("ANA", "bia", 10);
        List<Mensagem> b = repositorio.buscarUltimasPrivadas("bia", "ana", 10);

        assertEquals(List.of("1 ana->bia", "2 bia->ana", "3 Ana->BIA"),
                a.stream().map(Mensagem::getConteudo).toList());
        assertEquals(a, b, "a ordem dos dois nomes não importa");
    }

    @Test
    void historicoPrivadoRespeitaOLimiteMantendoAsMaisRecentes() {
        for (int i = 1; i <= 5; i++) {
            repositorio.salvar(Mensagem.privada("bia", "ana", "m" + i));
        }

        List<Mensagem> ultimas = repositorio.buscarUltimasPrivadas("ana", "bia", 2);

        assertEquals(List.of("m4", "m5"), ultimas.stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void conversaPrivadaComAUciFuncionaComoQualquerOutra() {
        repositorio.salvar(Mensagem.privada("UCI", "ana", "oi"));
        repositorio.salvar(Mensagem.privada("ana", "UCI", "olá!"));

        List<Mensagem> conversa = repositorio.buscarUltimasPrivadas("ana", "uci", 10);

        assertEquals(2, conversa.size());
        assertEquals("UCI", conversa.get(1).getRemetente());
    }

    @Test
    void quantidadeNaoPositivaEInvalidaNasNovasConsultas() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarUltimasDoGrupo("estudo", 0));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarUltimasPrivadas("a", "b", -1));
    }

    @Test
    void horarioDasMensagensDeGrupoEPrivadasEPreservado() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 20, 19, 30, 15);
        repositorio.salvar(Mensagem.grupo("estudo", "ana", "x", t));
        repositorio.salvar(Mensagem.privada("bia", "ana", "y", t));

        assertEquals(t, repositorio.buscarUltimasDoGrupo("estudo", 1).get(0).getHorario());
        assertEquals(t, repositorio.buscarUltimasPrivadas("ana", "bia", 1).get(0).getHorario());
    }

    // ---- migração de um banco criado pela versão anterior (só mensagens gerais) ----

    @Test
    void bancoDaVersaoAnteriorGanhaAsNovasColunasEAsMensagensViramGerais() throws SQLException {
        ConexaoFactory antigo = BancoDeTeste.novaFabrica();
        try (Connection c = antigo.obter(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE mensagem (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                    + "remetente VARCHAR(20) NOT NULL, conteudo VARCHAR(500) NOT NULL, horario TIMESTAMP NOT NULL)");
            st.execute("INSERT INTO mensagem (remetente, conteudo, horario) VALUES ('ana', 'mensagem antiga', "
                    + "TIMESTAMP '2026-10-01 10:00:00')");
        }

        JdbcMensagemRepository migrado = new JdbcMensagemRepository(antigo);
        migrado.criarEsquema();

        Mensagem lida = migrado.buscarUltimas(5).get(0);
        assertEquals("mensagem antiga", lida.getConteudo());
        assertEquals(Mensagem.Tipo.GERAL, lida.getTipo());
        assertNull(lida.getDestino());
        migrado.salvar(Mensagem.grupo("estudo", "ana", "nova")); // as colunas novas funcionam
        assertEquals(1, migrado.buscarUltimasDoGrupo("estudo", 5).size());
    }

    @Test
    void criarEsquemaPodeRodarVariasVezesSemPerderDados() {
        repositorio.salvar(new Mensagem("ana", "fica"));

        assertDoesNotThrow(repositorio::criarEsquema);
        assertDoesNotThrow(repositorio::criarEsquema);

        assertEquals(1, repositorio.buscarUltimas(5).size());
    }
}
