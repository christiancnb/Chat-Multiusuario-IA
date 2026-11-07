package br.unicesumar.chat.server;

import java.util.function.BooleanSupplier;

/** Espera (com limite de tempo) por uma condição que depende de outra thread. */
public final class Espera {

    private Espera() {
    }

    /** @return {@code true} se a condição ficou verdadeira em até {@code ms} milissegundos */
    public static boolean ate(BooleanSupplier condicao, long ms) {
        long limite = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < limite) {
            if (condicao.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condicao.getAsBoolean();
    }

    public static boolean ate(BooleanSupplier condicao) {
        return ate(condicao, 5000);
    }
}
