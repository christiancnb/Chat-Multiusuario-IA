package br.unicesumar.chat.server;

/**
 * Quem pode receber mensagens da {@link SalaChat}. Abstrai a conexão de rede:
 * a sala só conhece esta interface, o que permite testar o broadcast sem sockets.
 */
public interface Destinatario {

    /** Nome (único na sala) com que o cliente está identificado. */
    String getNome();

    /**
     * Entrega uma linha de texto ao cliente. Deve ser NÃO bloqueante: a sala
     * chama este método enquanto mantém seu lock.
     *
     * @return {@code false} se o cliente não consegue mais receber (desconectado
     *         ou lento demais); a sala o removerá da lista.
     */
    boolean enviar(String linha);

    /**
     * {@code true} se o cliente quer receber também as linhas de controle
     * (lista de online e de grupos). Só clientes gráficos pedem isso (comando {@code /modo gui});
     * clientes de console nunca as recebem.
     */
    default boolean aceitaControle() {
        return false;
    }

    /** Passa a aceitar linhas de controle. */
    default void ativarControle() {
    }
}
