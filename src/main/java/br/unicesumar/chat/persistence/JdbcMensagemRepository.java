package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ponto do sistema (junto com {@link JdbcGrupoRepository}) que conversa com o banco via JDBC.
 *
 * <p>É seguro para uso por várias threads: não guarda estado mutável e cada
 * método usa uma conexão própria, fechada ao final (try-with-resources).</p>
 */
public class JdbcMensagemRepository implements MensagemRepository {

    private static final String SQL_INSERIR =
            "INSERT INTO mensagem (remetente, conteudo, horario, tipo, destino) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_SELECIONAR =
            "SELECT id, remetente, conteudo, horario, tipo, destino FROM mensagem ";
    private static final String SQL_ULTIMAS_GERAIS =
            SQL_SELECIONAR + "WHERE tipo = 'GERAL' ORDER BY id DESC LIMIT ?";
    private static final String SQL_ULTIMAS_DO_GRUPO =
            SQL_SELECIONAR + "WHERE tipo = 'GRUPO' AND destino = ? ORDER BY id DESC LIMIT ?";
    private static final String SQL_ULTIMAS_PRIVADAS =
            SQL_SELECIONAR + "WHERE tipo = 'PRIVADA' AND "
                    + "((LOWER(remetente) = ? AND LOWER(destino) = ?) OR (LOWER(remetente) = ? AND LOWER(destino) = ?)) "
                    + "ORDER BY id DESC LIMIT ?";
    private static final String SQL_BUSCA =
            SQL_SELECIONAR + "WHERE LOWER(conteudo) LIKE ? ESCAPE '!' ORDER BY id DESC LIMIT ?";
    private static final String RECURSO_ESQUEMA = "/schema.sql";

    private final ConexaoFactory conexoes;

    public JdbcMensagemRepository(ConexaoFactory conexoes) {
        this.conexoes = Objects.requireNonNull(conexoes);
    }

    /** Executa o script {@code schema.sql} (idempotente: usa IF NOT EXISTS). */
    public void criarEsquema() {
        String script = lerEsquema();
        try (Connection conexao = conexoes.obter(); Statement comando = conexao.createStatement()) {
            for (String instrucao : separarInstrucoes(script)) {
                comando.execute(instrucao);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao criar o esquema do banco.", e);
        }
    }

    @Override
    public Mensagem salvar(Mensagem mensagem) {
        Objects.requireNonNull(mensagem, "mensagem");
        try (Connection conexao = conexoes.obter();
             PreparedStatement ps = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mensagem.getRemetente());
            ps.setString(2, mensagem.getConteudo());
            ps.setTimestamp(3, Timestamp.valueOf(mensagem.getHorario()));
            ps.setString(4, mensagem.getTipo().name());
            if (mensagem.getDestino() == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, mensagem.getDestino());
            }
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    return mensagem.comId(chaves.getLong(1));
                }
            }
            throw new PersistenciaException("O banco não devolveu o id da mensagem salva.");
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao salvar a mensagem.", e);
        }
    }

    @Override
    public List<Mensagem> buscarUltimas(int n) {
        return consultar(SQL_ULTIMAS_GERAIS, n);
    }

    @Override
    public List<Mensagem> buscarUltimasDoGrupo(String grupo, int n) {
        return consultar(SQL_ULTIMAS_DO_GRUPO, n, Objects.requireNonNull(grupo, "grupo"));
    }

    @Override
    public List<Mensagem> buscarUltimasPrivadas(String usuarioA, String usuarioB, int n) {
        String a = Usuario.chave(usuarioA);
        String b = Usuario.chave(usuarioB);
        return consultar(SQL_ULTIMAS_PRIVADAS, n, a, b, b, a);
    }

    @Override
    public List<Mensagem> buscarPorTexto(String termo, int limite) {
        if (termo == null || termo.isBlank()) {
            throw new IllegalArgumentException("Informe o texto a buscar.");
        }
        String escapado = termo.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return consultar(SQL_BUSCA, limite, "%" + escapado + "%");
    }

    /** Executa a consulta com os parâmetros de texto e, por último, o limite {@code n}. */
    private List<Mensagem> consultar(String sql, int n, String... parametros) {
        if (n <= 0) {
            throw new IllegalArgumentException("A quantidade de mensagens deve ser positiva.");
        }
        List<Mensagem> resultado = new ArrayList<>();
        try (Connection conexao = conexoes.obter(); PreparedStatement ps = conexao.prepareStatement(sql)) {
            int indice = 1;
            for (String parametro : parametros) {
                ps.setString(indice++, parametro);
            }
            ps.setInt(indice, n);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new Mensagem(
                            rs.getLong("id"),
                            Mensagem.Tipo.valueOf(rs.getString("tipo")),
                            rs.getString("destino"),
                            rs.getString("remetente"),
                            rs.getString("conteudo"),
                            rs.getTimestamp("horario").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao buscar o histórico.", e);
        }
        // A consulta devolve da mais nova para a mais antiga; o chat exibe em ordem cronológica.
        Collections.reverse(resultado);
        return resultado;
    }

    private String lerEsquema() {
        try (InputStream in = JdbcMensagemRepository.class.getResourceAsStream(RECURSO_ESQUEMA)) {
            if (in == null) {
                throw new PersistenciaException("Arquivo " + RECURSO_ESQUEMA + " não encontrado no classpath.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenciaException("Falha ao ler " + RECURSO_ESQUEMA, e);
        }
    }

    /** Remove comentários ("-- ...") e separa as instruções pelo ponto e vírgula. */
    static List<String> separarInstrucoes(String script) {
        StringBuilder semComentarios = new StringBuilder();
        for (String linha : script.split("\\R")) {
            if (!linha.trim().startsWith("--")) {
                semComentarios.append(linha).append('\n');
            }
        }
        List<String> instrucoes = new ArrayList<>();
        for (String parte : semComentarios.toString().split(";")) {
            String instrucao = parte.trim();
            if (!instrucao.isEmpty()) {
                instrucoes.add(instrucao);
            }
        }
        return instrucoes;
    }
}
