package br.unicesumar.chat.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Entidade de domínio que representa quem está conversando no chat.
 *
 * <p>Regra de negócio: o nome tem de 2 a 20 caracteres (letras, números,
 * '_', '.' ou '-') e não pode conter espaços, pois é usado como identificador
 * na linha de cada mensagem. A comparação de nomes ignora maiúsculas/minúsculas
 * ("Ana" e "ana" são o mesmo usuário).</p>
 */
public final class Usuario {

    private static final Pattern NOME_VALIDO = Pattern.compile("[\\p{L}\\p{N}_.-]{2,20}");

    /** Nome da assistente virtual do chat. É reservado: nenhum usuário pode usá-lo. */
    public static final String NOME_UCI = "UCI";

    private final String nome;

    public Usuario(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do usuário é obrigatório.");
        }
        String limpo = nome.trim();
        if (!NOME_VALIDO.matcher(limpo).matches()) {
            throw new IllegalArgumentException(
                    "Nome inválido: use de 2 a 20 caracteres (letras, números, '_', '.' ou '-'), sem espaços.");
        }
        if (reservado(limpo)) {
            throw new IllegalArgumentException(
                    "O nome '" + NOME_UCI + "' é reservado para a assistente do chat.");
        }
        this.nome = limpo;
    }

    public static boolean nomeValido(String nome) {
        return nome != null && NOME_VALIDO.matcher(nome.trim()).matches() && !reservado(nome);
    }

    /** {@code true} se o nome é o da assistente (sem diferenciar maiúsculas). */
    public static boolean reservado(String nome) {
        return nome != null && nome.trim().equalsIgnoreCase(NOME_UCI);
    }

    /** Forma canônica usada para comparar nomes (minúsculas, sem espaços nas pontas). */
    public static String chave(String nome) {
        return nome == null ? "" : nome.trim().toLowerCase(Locale.ROOT);
    }

    public String getNome() {
        return nome;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Usuario outro)) return false;
        return nome.equalsIgnoreCase(outro.nome);
    }

    @Override
    public int hashCode() {
        return nome.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return nome;
    }
}
