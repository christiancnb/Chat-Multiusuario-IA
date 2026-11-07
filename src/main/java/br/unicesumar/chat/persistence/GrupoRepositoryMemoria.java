package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Grupo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementação de {@link GrupoRepository} que guarda tudo em memória (os dados somem ao
 * encerrar o programa). Serve como padrão quando não há banco e nos testes.
 * É thread-safe (métodos sincronizados).
 */
public class GrupoRepositoryMemoria implements GrupoRepository {

    private final Map<String, Grupo> grupos = new LinkedHashMap<>();

    @Override
    public synchronized List<Grupo> carregarTodos() {
        List<Grupo> copia = new ArrayList<>();
        for (Grupo g : grupos.values()) {
            copia.add(g.copiar());
        }
        return copia;
    }

    @Override
    public synchronized void criar(Grupo grupo) {
        if (grupos.containsKey(grupo.getNome())) {
            throw new PersistenciaException("O grupo '" + grupo.getNome() + "' já existe.");
        }
        grupos.put(grupo.getNome(), grupo.copiar());
    }

    @Override
    public synchronized void adicionarMembro(String grupo, String usuario) {
        existente(grupo).adicionar(usuario);
    }

    @Override
    public synchronized void removerMembro(String grupo, String usuario) {
        existente(grupo).remover(usuario);
    }

    private Grupo existente(String nome) {
        Grupo g = grupos.get(nome);
        if (g == null) {
            throw new PersistenciaException("O grupo '" + nome + "' não existe.");
        }
        return g;
    }
}
