package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Grupo;
import br.unicesumar.chat.model.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link GrupoRepository} sobre JDBC (tabelas {@code grupo} e {@code grupo_membro}).
 * Sem estado: cada operação usa a sua própria conexão, portanto é seguro entre threads.
 */
public class JdbcGrupoRepository implements GrupoRepository {

    private static final String SQL_GRUPOS = "SELECT nome, criador FROM grupo ORDER BY nome";
    private static final String SQL_MEMBROS = "SELECT grupo, usuario FROM grupo_membro";
    private static final String SQL_INSERIR_GRUPO = "INSERT INTO grupo (nome, criador, criado_em) VALUES (?, ?, ?)";
    private static final String SQL_INSERIR_MEMBRO = "INSERT INTO grupo_membro (grupo, usuario) VALUES (?, ?)";
    private static final String SQL_REMOVER_MEMBRO = "DELETE FROM grupo_membro WHERE grupo = ? AND usuario = ?";

    private final ConexaoFactory conexoes;

    public JdbcGrupoRepository(ConexaoFactory conexoes) {
        this.conexoes = Objects.requireNonNull(conexoes);
    }

    @Override
    public List<Grupo> carregarTodos() {
        Map<String, Grupo> porNome = new LinkedHashMap<>();
        try (Connection conexao = conexoes.obter()) {
            try (Statement st = conexao.createStatement(); ResultSet rs = st.executeQuery(SQL_GRUPOS)) {
                while (rs.next()) {
                    Grupo g = new Grupo(rs.getString("nome"), rs.getString("criador"));
                    porNome.put(g.getNome(), g);
                }
            }
            try (Statement st = conexao.createStatement(); ResultSet rs = st.executeQuery(SQL_MEMBROS)) {
                while (rs.next()) {
                    Grupo g = porNome.get(rs.getString("grupo"));
                    if (g != null) {
                        g.adicionar(rs.getString("usuario"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao carregar os grupos.", e);
        }
        return new ArrayList<>(porNome.values());
    }

    /** Insere o grupo e seus membros em uma única transação (ou tudo, ou nada). */
    @Override
    public void criar(Grupo grupo) {
        Objects.requireNonNull(grupo, "grupo");
        try (Connection conexao = conexoes.obter()) {
            conexao.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conexao.prepareStatement(SQL_INSERIR_GRUPO)) {
                    ps.setString(1, grupo.getNome());
                    ps.setString(2, grupo.getCriador());
                    ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conexao.prepareStatement(SQL_INSERIR_MEMBRO)) {
                    for (String membro : grupo.getMembros()) {
                        ps.setString(1, grupo.getNome());
                        ps.setString(2, membro);
                        ps.executeUpdate();
                    }
                }
                conexao.commit();
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao criar o grupo '" + grupo.getNome() + "'.", e);
        }
    }

    @Override
    public void adicionarMembro(String grupo, String usuario) {
        try (Connection conexao = conexoes.obter(); PreparedStatement ps = conexao.prepareStatement(SQL_INSERIR_MEMBRO)) {
            ps.setString(1, grupo);
            ps.setString(2, Usuario.chave(usuario));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao adicionar o membro ao grupo.", e);
        }
    }

    @Override
    public void removerMembro(String grupo, String usuario) {
        try (Connection conexao = conexoes.obter(); PreparedStatement ps = conexao.prepareStatement(SQL_REMOVER_MEMBRO)) {
            ps.setString(1, grupo);
            ps.setString(2, Usuario.chave(usuario));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao remover o membro do grupo.", e);
        }
    }
}
