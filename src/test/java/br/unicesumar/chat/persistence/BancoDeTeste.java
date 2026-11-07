package br.unicesumar.chat.persistence;

import java.util.UUID;

/** Cria um banco H2 em memória isolado para cada teste. */
public final class BancoDeTeste {

    private BancoDeTeste() {
    }

    public static ConexaoFactory novaFabrica() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        return ConexaoFactory.jdbc(url, "sa", "");
    }

    public static JdbcMensagemRepository novoRepositorio() {
        JdbcMensagemRepository repositorio = new JdbcMensagemRepository(novaFabrica());
        repositorio.criarEsquema();
        return repositorio;
    }
}
