package br.unicesumar.chat.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fábrica de conexões JDBC. Cada operação do repositório abre e fecha a sua
 * própria conexão, então não há conexão compartilhada entre threads.
 */
@FunctionalInterface
public interface ConexaoFactory {

    Connection obter() throws SQLException;

    static ConexaoFactory jdbc(String url, String usuario, String senha) {
        return () -> DriverManager.getConnection(url, usuario, senha);
    }

    /**
     * Configuração padrão, sobrescrevível por variáveis de ambiente:
     * {@code CHAT_DB_URL}, {@code CHAT_DB_USER} e {@code CHAT_DB_PASSWORD}.
     * Sem configuração, usa um arquivo H2 em {@code ./data/chat}.
     */
    static ConexaoFactory padrao() {
        String url = valor("CHAT_DB_URL", "jdbc:h2:file:./data/chat");
        String usuario = valor("CHAT_DB_USER", "sa");
        String senha = valor("CHAT_DB_PASSWORD", "");
        return jdbc(url, usuario, senha);
    }

    private static String valor(String variavel, String padrao) {
        String v = System.getenv(variavel);
        return (v == null || v.isBlank()) ? padrao : v;
    }
}
