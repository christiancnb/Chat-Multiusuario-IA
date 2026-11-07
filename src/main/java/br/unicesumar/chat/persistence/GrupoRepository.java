package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Grupo;

import java.util.List;

/**
 * Contrato de persistência de grupos e de seus membros. Os grupos são carregados uma vez
 * na subida do servidor; depois, cada alteração é gravada aqui (write-through).
 */
public interface GrupoRepository {

    /** Todos os grupos, cada um já com seus membros. */
    List<Grupo> carregarTodos();

    /** Grava um grupo novo junto com os membros que ele já tem (o criador). */
    void criar(Grupo grupo);

    void adicionarMembro(String grupo, String usuario);

    void removerMembro(String grupo, String usuario);
}
