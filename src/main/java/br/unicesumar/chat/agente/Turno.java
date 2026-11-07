package br.unicesumar.chat.agente;

import java.util.Objects;

/** Uma fala dentro da conversa com o agente: de quem (usuário ou agente) e o texto. */
public record Turno(Papel papel, String texto) {

    public enum Papel { USUARIO, AGENTE }

    public Turno {
        Objects.requireNonNull(papel, "papel");
        Objects.requireNonNull(texto, "texto");
    }
}
