package br.unicesumar.chat.agente;

/**
 * Falha ao obter uma resposta do agente. {@link #getMessage()} é um texto amigável que pode
 * ser mostrado aos usuários do chat; {@link #getDetalhe()} traz o detalhe técnico, que deve
 * ir só para o log do servidor (nunca para os clientes, e nunca contém a chave da API).
 */
public class AgenteException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String detalhe;

    public AgenteException(String mensagemAmigavel, String detalheTecnico, Throwable causa) {
        super(mensagemAmigavel, causa);
        this.detalhe = detalheTecnico;
    }

    public AgenteException(String mensagemAmigavel, String detalheTecnico) {
        this(mensagemAmigavel, detalheTecnico, null);
    }

    public String getDetalhe() {
        return detalhe == null ? getMessage() : detalhe;
    }
}
