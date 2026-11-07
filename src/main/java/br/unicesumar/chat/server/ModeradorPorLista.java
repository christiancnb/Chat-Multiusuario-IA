package br.unicesumar.chat.server;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Troca palavras ofensivas de uma lista por asteriscos (palavra inteira, sem diferenciar acento ou caixa). */
public final class ModeradorPorLista implements ModeradorDeMensagens {

    public static final List<String> PALAVRAS_PADRAO =
            List.of("idiota", "imbecil", "babaca", "otario", "burro", "retardado");

    private final Pattern padrao;

    public ModeradorPorLista(List<String> palavras) {
        String alternativas = String.join("|", palavras.stream().map(Pattern::quote).toList());
        this.padrao = Pattern.compile("(?<![a-z0-9])(" + alternativas + ")(?![a-z0-9])");
    }

    public static ModeradorPorLista padrao() {
        return new ModeradorPorLista(PALAVRAS_PADRAO);
    }

    @Override
    public String moderar(String texto) {
        // Normalizar com NFD só para localizar; o texto original é preservado fora das palavras trocadas.
        StringBuilder base = new StringBuilder();
        List<Integer> origem = new java.util.ArrayList<>();
        for (int i = 0; i < texto.length(); i++) {
            String parte = Normalizer.normalize(String.valueOf(texto.charAt(i)), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
            for (int k = 0; k < parte.length(); k++) {
                base.append(parte.charAt(k));
                origem.add(i);
            }
        }
        Matcher m = padrao.matcher(base);
        StringBuilder saida = new StringBuilder();
        int cursor = 0;
        while (m.find()) {
            int de = origem.get(m.start());
            int ate = origem.get(m.end() - 1) + 1;
            saida.append(texto, cursor, de).append("*".repeat(ate - de));
            cursor = ate;
        }
        return saida.append(texto.substring(cursor)).toString();
    }
}
