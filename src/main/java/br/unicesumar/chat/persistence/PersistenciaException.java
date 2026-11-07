package br.unicesumar.chat.persistence;

/** Erro de acesso ao banco. Encapsula {@link java.sql.SQLException} para não vazar JDBC às outras camadas. */
public class PersistenciaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenciaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public PersistenciaException(String mensagem) {
        super(mensagem);
    }
}
