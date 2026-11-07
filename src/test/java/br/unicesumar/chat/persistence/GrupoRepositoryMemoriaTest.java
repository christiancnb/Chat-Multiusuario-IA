package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Grupo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrupoRepositoryMemoriaTest {

    @Test
    void comecaVazio() {
        assertTrue(new GrupoRepositoryMemoria().carregarTodos().isEmpty());
    }

    @Test
    void criaAdicionaERemoveMembros() {
        GrupoRepositoryMemoria repo = new GrupoRepositoryMemoria();
        Grupo g = new Grupo("estudo", "ana");
        g.adicionar("ana");
        repo.criar(g);

        repo.adicionarMembro("estudo", "Bia");
        assertEquals(Set.of("ana", "bia"), repo.carregarTodos().get(0).getMembros());

        repo.removerMembro("estudo", "ANA");
        assertEquals(Set.of("bia"), repo.carregarTodos().get(0).getMembros());
    }

    @Test
    void carregarDevolveCopiasQueNaoAlteramOQueEstaGuardado() {
        GrupoRepositoryMemoria repo = new GrupoRepositoryMemoria();
        Grupo g = new Grupo("estudo", "ana");
        g.adicionar("ana");
        repo.criar(g);

        List<Grupo> copia = repo.carregarTodos();
        copia.get(0).adicionar("intruso");
        g.adicionar("outro"); // alterar o original depois de criar também não afeta o guardado

        assertEquals(Set.of("ana"), repo.carregarTodos().get(0).getMembros());
    }

    @Test
    void grupoRepetidoOuInexistenteGeraPersistenciaException() {
        GrupoRepositoryMemoria repo = new GrupoRepositoryMemoria();
        repo.criar(new Grupo("estudo", "ana"));

        assertThrows(PersistenciaException.class, () -> repo.criar(new Grupo("estudo", "bia")));
        assertThrows(PersistenciaException.class, () -> repo.adicionarMembro("nada", "ana"));
        assertThrows(PersistenciaException.class, () -> repo.removerMembro("nada", "ana"));
    }
}
