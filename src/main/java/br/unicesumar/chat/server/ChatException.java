package br.unicesumar.chat.server;

/**
 * Violação de uma regra do chat causada pelo pedido do usuário (usuário offline, grupo
 * inexistente, não ser membro etc.). A mensagem é própria para ser mostrada ao usuário.
 * Difere de {@link IllegalArgumentException}, que indica dado inválido (nome ou texto fora do formato).
 */
public class ChatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ChatException(String mensagem) {
        super(mensagem);
    }
}
