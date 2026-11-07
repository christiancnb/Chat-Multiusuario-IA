package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Grupo;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.GrupoRepository;
import br.unicesumar.chat.persistence.PersistenciaException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cadastro dos grupos em memória, com gravação imediata no {@link GrupoRepository}
 * (write-through): a memória só é alterada depois que o banco confirma.
 *
 * <p>Os grupos são carregados uma vez, ao construir o registro. <b>Não é thread-safe</b>:
 * só é usado por {@link SalaChat}, sempre sob o lock da sala.</p>
 */
class RegistroGrupos {

    private static final Logger LOG = Logger.getLogger(RegistroGrupos.class.getName());

    private final GrupoRepository repositorio;
    private final Map<String, Grupo> porNome = new TreeMap<>();

    RegistroGrupos(GrupoRepository repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio);
        for (Grupo g : repositorio.carregarTodos()) {
            porNome.put(g.getNome(), g);
        }
    }

    /** Cria o grupo com o criador como primeiro membro. */
    Grupo criar(String nomeBruto, String criador) {
        Grupo grupo = new Grupo(nomeBruto, criador); // valida e normaliza o nome
        if (porNome.containsKey(grupo.getNome())) {
            throw new ChatException("O grupo #" + grupo.getNome() + " já existe. Use /grupo entrar " + grupo.getNome() + ".");
        }
        grupo.adicionar(criador);
        gravar(() -> repositorio.criar(grupo), "criar o grupo");
        porNome.put(grupo.getNome(), grupo);
        return grupo;
    }

    /** @throws ChatException se o grupo não existe */
    Grupo exigir(String nomeBruto) {
        String chave = chave(nomeBruto);
        Grupo grupo = porNome.get(chave);
        if (grupo == null) {
            throw new ChatException("O grupo #" + chave + " não existe. Use /grupos para ver os existentes.");
        }
        return grupo;
    }

    void adicionarMembro(Grupo grupo, String usuario) {
        gravar(() -> repositorio.adicionarMembro(grupo.getNome(), Usuario.chave(usuario)), "entrar no grupo");
        grupo.adicionar(usuario);
    }

    void removerMembro(Grupo grupo, String usuario) {
        gravar(() -> repositorio.removerMembro(grupo.getNome(), Usuario.chave(usuario)), "sair do grupo");
        grupo.remover(usuario);
    }

    /** Nomes de todos os grupos, em ordem alfabética. */
    List<String> nomes() {
        return new ArrayList<>(porNome.keySet());
    }

    /** Nomes dos grupos de que o usuário é membro, em ordem alfabética. */
    List<String> nomesDe(String usuario) {
        List<String> nomes = new ArrayList<>();
        for (Grupo g : porNome.values()) {
            if (g.contem(usuario)) {
                nomes.add(g.getNome());
            }
        }
        return nomes;
    }

    List<Grupo> gruposDe(String usuario) {
        List<Grupo> grupos = new ArrayList<>();
        for (Grupo g : porNome.values()) {
            if (g.contem(usuario)) {
                grupos.add(g);
            }
        }
        return grupos;
    }

    private static String chave(String nomeBruto) {
        String limpo = nomeBruto == null ? "" : nomeBruto.trim();
        if (limpo.startsWith("#")) {
            limpo = limpo.substring(1);
        }
        return Usuario.chave(limpo);
    }

    private static void gravar(Runnable operacao, String acao) {
        try {
            operacao.run();
        } catch (PersistenciaException e) {
            LOG.log(Level.SEVERE, "Falha ao " + acao + ".", e);
            throw new ChatException("Não foi possível concluir agora (falha ao gravar no banco). Tente novamente.");
        }
    }
}
