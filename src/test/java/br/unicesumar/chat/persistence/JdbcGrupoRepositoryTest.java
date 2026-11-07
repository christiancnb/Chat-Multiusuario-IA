package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Grupo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grupos e membros gravados no banco (H2 em memória). */
class JdbcGrupoRepositoryTest {

    private ConexaoFactory fabrica;
    private JdbcGrupoRepository repositorio;

    @BeforeEach
    void criarBanco() {
        fabrica = BancoDeTeste.novaFabrica();
        new JdbcMensagemRepository(fabrica).criarEsquema(); // o script cria também as tabelas de grupo
        repositorio = new JdbcGrupoRepository(fabrica);
    }

    @Test
    void bancoNovoNaoTemGrupos() {
        assertTrue(repositorio.carregarTodos().isEmpty());
    }

    @Test
    void criarGravaOGrupoEOsMembrosQueEleJaTem() {
        repositorio.criar(grupoCom("estudo", "ana", "ana", "bia"));

        List<Grupo> carregados = repositorio.carregarTodos();

        assertEquals(1, carregados.size());
        Grupo g = carregados.get(0);
        assertEquals("estudo", g.getNome());
        assertEquals("ana", g.getCriador());
        assertEquals(java.util.Set.of("ana", "bia"), g.getMembros());
    }

    @Test
    void adicionarERemoverMembroPersistem() {
        repositorio.criar(grupoCom("estudo", "ana", "ana"));

        repositorio.adicionarMembro("estudo", "bia");
        assertEquals(java.util.Set.of("ana", "bia"), repositorio.carregarTodos().get(0).getMembros());

        repositorio.removerMembro("estudo", "bia");
        assertEquals(java.util.Set.of("ana"), repositorio.carregarTodos().get(0).getMembros());
    }

    @Test
    void nomesDeMembrosSaoGuardadosEmMinusculas() {
        repositorio.criar(grupoCom("estudo", "Ana", "Ana"));

        repositorio.adicionarMembro("estudo", "BIA");

        assertEquals(java.util.Set.of("ana", "bia"), repositorio.carregarTodos().get(0).getMembros());
    }

    @Test
    void removerMembroQueNaoExisteNaoDaErro() {
        repositorio.criar(grupoCom("estudo", "ana", "ana"));
        repositorio.removerMembro("estudo", "fantasma");
        assertEquals(java.util.Set.of("ana"), repositorio.carregarTodos().get(0).getMembros());
    }

    @Test
    void grupoComNomeRepetidoFalhaENaoDeixaMembrosPelaMetade() {
        repositorio.criar(grupoCom("estudo", "ana", "ana"));

        assertThrows(PersistenciaException.class,
                () -> repositorio.criar(grupoCom("estudo", "bia", "bia", "caio")));

        List<Grupo> carregados = repositorio.carregarTodos();
        assertEquals(1, carregados.size());
        assertEquals(java.util.Set.of("ana"), carregados.get(0).getMembros(), "a transação foi desfeita (rollback)");
    }

    @Test
    void membroRepetidoNoMesmoGrupoFalha() {
        repositorio.criar(grupoCom("estudo", "ana", "ana"));
        assertThrows(PersistenciaException.class, () -> repositorio.adicionarMembro("estudo", "ANA"));
    }

    @Test
    void osDadosSobrevivemAUmaNovaInstanciaDoRepositorio() {
        repositorio.criar(grupoCom("estudo", "ana", "ana"));
        repositorio.adicionarMembro("estudo", "bia");

        List<Grupo> depois = new JdbcGrupoRepository(fabrica).carregarTodos();

        assertEquals(1, depois.size());
        assertEquals(java.util.Set.of("ana", "bia"), depois.get(0).getMembros());
    }

    @Test
    void varioGruposVoltamEmOrdemAlfabetica() {
        repositorio.criar(grupoCom("time", "ana", "ana"));
        repositorio.criar(grupoCom("estudo", "bia", "bia"));

        List<Grupo> carregados = repositorio.carregarTodos();

        assertEquals("estudo", carregados.get(0).getNome());
        assertEquals("time", carregados.get(1).getNome());
    }

    private static Grupo grupoCom(String nome, String criador, String... membros) {
        Grupo g = new Grupo(nome, criador);
        for (String m : membros) {
            g.adicionar(m);
        }
        return g;
    }
}
