package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Usuario;

import java.util.Objects;

/**
 * Uma conversa exibida em uma aba: a sala geral, um grupo ou um bate-papo privado com alguém.
 * Sabe como transformar o que o usuário digitou na linha de protocolo a enviar ao servidor.
 * Duas conversas são iguais quando têm o mesmo tipo e a mesma chave ("Ana" e "ana" são a mesma).
 */
public final class Conversa {

    public enum Tipo { GERAL, GRUPO, PRIVADA }

    /** A sala geral (sempre existe). */
    public static final Conversa GERAL = new Conversa(Tipo.GERAL, "geral", "Geral");

    private final Tipo tipo;
    private final String chave;
    private final String titulo;

    private Conversa(Tipo tipo, String chave, String titulo) {
        this.tipo = tipo;
        this.chave = chave;
        this.titulo = titulo;
    }

    /** Conversa de grupo; aceita o nome com ou sem '#'. */
    public static Conversa grupo(String nome) {
        String limpo = nome.trim();
        if (limpo.startsWith("#")) {
            limpo = limpo.substring(1);
        }
        String chave = Usuario.chave(limpo);
        return new Conversa(Tipo.GRUPO, chave, "#" + chave);
    }

    /** Conversa privada com o usuário; o nome é mantido como foi informado, para exibição. */
    public static Conversa privada(String nome) {
        String limpo = nome.trim();
        return new Conversa(Tipo.PRIVADA, Usuario.chave(limpo), limpo);
    }

    public Tipo tipo() {
        return tipo;
    }

    /** Identificador em minúsculas (nome do grupo ou do usuário). */
    public String chave() {
        return chave;
    }

    /** Texto do título da aba. */
    public String titulo() {
        return titulo;
    }

    /**
     * Linha a enviar ao servidor para o texto digitado nesta conversa. Textos que começam com
     * '/' são comandos e seguem como estão, em qualquer aba.
     */
    public String comandoDeEnvio(String texto) {
        if (texto.startsWith("/")) {
            return texto;
        }
        return switch (tipo) {
            case GERAL -> texto;
            case GRUPO -> "/g " + chave + " " + texto;
            case PRIVADA -> "/msg " + titulo + " " + texto;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Conversa outra)) return false;
        return tipo == outra.tipo && chave.equals(outra.chave);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipo, chave);
    }

    @Override
    public String toString() {
        return tipo + ":" + chave;
    }
}
