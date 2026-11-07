package br.unicesumar.chat.server;

/**
 * Porta de moderação: recebe o texto de uma mensagem e devolve o texto a ser entregue
 * (igual ao original quando está tudo bem). Trocar a regra é trocar o adaptador.
 */
@FunctionalInterface
public interface ModeradorDeMensagens {

    /** Não altera nada. */
    ModeradorDeMensagens NENHUM = texto -> texto;

    String moderar(String texto);
}
