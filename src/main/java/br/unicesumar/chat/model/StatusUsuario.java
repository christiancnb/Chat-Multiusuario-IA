package br.unicesumar.chat.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/** Como o usuário quer aparecer para os outros. Valor de domínio (não depende de banco nem de tela). */
public enum StatusUsuario {
    DISPONIVEL("disponivel"),
    OCUPADO("ocupado"),
    AUSENTE("ausente");

    private final String rotulo;

    StatusUsuario(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Texto usado nos comandos e no protocolo (minúsculas, sem acento). */
    public String rotulo() {
        return rotulo;
    }

    /** Aceita com ou sem acento e sem diferenciar maiúsculas ("Disponível" = "disponivel"). */
    public static Optional<StatusUsuario> de(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String limpo = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        for (StatusUsuario s : values()) {
            if (s.rotulo.equals(limpo)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
