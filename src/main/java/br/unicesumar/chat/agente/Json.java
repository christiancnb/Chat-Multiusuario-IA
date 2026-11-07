package br.unicesumar.chat.agente;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON mínimo, só o necessário para falar com a API (o projeto não depende de nenhuma
 * biblioteca de JSON). {@link #texto(String)} gera strings JSON escapadas e
 * {@link #interpretar(String)} lê um documento JSON em {@code Map}, {@code List},
 * {@code String}, {@code Double}, {@code Boolean} ou {@code null}.
 */
final class Json {

    private static final int PROFUNDIDADE_MAXIMA = 64;

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** Devolve {@code valor} como string JSON, já entre aspas e com os escapes necessários. */
    static String texto(String valor) {
        StringBuilder sb = new StringBuilder(valor.length() + 2).append('"');
        for (int k = 0; k < valor.length(); k++) {
            char c = valor.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** @throws IllegalArgumentException se o texto não for um JSON válido */
    static Object interpretar(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON nulo.");
        }
        Json p = new Json(json);
        p.espacos();
        Object valor = p.valor(0);
        p.espacos();
        if (p.i != json.length()) {
            throw p.erro("conteúdo extra após o fim do JSON");
        }
        return valor;
    }

    // ---- leitura ----

    private Object valor(int profundidade) {
        if (profundidade > PROFUNDIDADE_MAXIMA) {
            throw erro("JSON aninhado demais");
        }
        if (i >= s.length()) {
            throw erro("fim inesperado");
        }
        char c = s.charAt(i);
        if (c == '{') return objeto(profundidade);
        if (c == '[') return lista(profundidade);
        if (c == '"') return string();
        if (c == 't') return literal("true", Boolean.TRUE);
        if (c == 'f') return literal("false", Boolean.FALSE);
        if (c == 'n') return literal("null", null);
        if (c == '-' || (c >= '0' && c <= '9')) return numero();
        throw erro("caractere inesperado '" + c + "'");
    }

    private Map<String, Object> objeto(int profundidade) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        i++; // {
        espacos();
        if (consumir('}')) {
            return mapa;
        }
        while (true) {
            espacos();
            if (i >= s.length() || s.charAt(i) != '"') {
                throw erro("era esperado o nome de um campo");
            }
            String chave = string();
            espacos();
            if (!consumir(':')) {
                throw erro("era esperado ':'");
            }
            espacos();
            mapa.put(chave, valor(profundidade + 1));
            espacos();
            if (consumir(',')) {
                continue;
            }
            if (consumir('}')) {
                return mapa;
            }
            throw erro("era esperado ',' ou '}'");
        }
    }

    private List<Object> lista(int profundidade) {
        List<Object> itens = new ArrayList<>();
        i++; // [
        espacos();
        if (consumir(']')) {
            return itens;
        }
        while (true) {
            espacos();
            itens.add(valor(profundidade + 1));
            espacos();
            if (consumir(',')) {
                continue;
            }
            if (consumir(']')) {
                return itens;
            }
            throw erro("era esperado ',' ou ']'");
        }
    }

    private String string() {
        i++; // aspas de abertura
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) {
                break;
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) {
                        throw erro("escape \\u incompleto");
                    }
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw erro("escape \\u inválido");
                    }
                    i += 4;
                }
                default -> throw erro("escape inválido '\\" + e + "'");
            }
        }
        throw erro("string não terminada");
    }

    private Double numero() {
        int inicio = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        try {
            return Double.valueOf(s.substring(inicio, i));
        } catch (NumberFormatException e) {
            throw erro("número inválido");
        }
    }

    private Object literal(String texto, Object valor) {
        if (!s.startsWith(texto, i)) {
            throw erro("valor inválido");
        }
        i += texto.length();
        return valor;
    }

    private boolean consumir(char c) {
        if (i < s.length() && s.charAt(i) == c) {
            i++;
            return true;
        }
        return false;
    }

    private void espacos() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private IllegalArgumentException erro(String motivo) {
        return new IllegalArgumentException("JSON inválido na posição " + i + ": " + motivo + ".");
    }
}
