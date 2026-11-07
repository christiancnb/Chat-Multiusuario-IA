package br.unicesumar.chat.agente;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Jeito de falar da UCI, escolhido por cada usuário com {@code /uci modo}. A instrução vai junto
 * da pergunta, entre colchetes duplos; {@link AgenteLocal} a ignora e os agentes de IA a seguem.
 */
public enum Personalidade {
    PADRAO("padrao", ""),
    FORMAL("formal", "Responda de forma formal, objetiva e educada."),
    DESCONTRAIDA("descontraida", "Responda de forma descontraída, leve e bem-humorada."),
    PROFESSORA("professora", "Responda como uma professora paciente: explique passo a passo, com um exemplo.");

    private final String nome;
    private final String instrucao;

    Personalidade(String nome, String instrucao) {
        this.nome = nome;
        this.instrucao = instrucao;
    }

    public String nome() {
        return nome;
    }

    /** Texto a acrescentar à pergunta; vazio para o modo padrão. */
    public String instrucao() {
        return instrucao.isEmpty() ? "" : "[[" + instrucao + "]] ";
    }

    public static Optional<Personalidade> de(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String limpo = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        for (Personalidade p : values()) {
            if (p.nome.equals(limpo)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Remove as instruções {@code [[...]]} de um texto (usado por quem não as interpreta). */
    public static String semInstrucao(String texto) {
        return texto.replaceAll("\\[\\[.*?]]\\s*", "");
    }
}
