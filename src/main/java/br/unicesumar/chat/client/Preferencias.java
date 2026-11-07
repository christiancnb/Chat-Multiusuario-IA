package br.unicesumar.chat.client;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Preferências de aparência e de avisos do usuário, guardadas no computador dele
 * ({@link java.util.prefs.Preferences}). É só dado e regra de validação, sem Swing, e por isso
 * testável. Valores inválidos gravados antes (ou por outra versão) caem no padrão.
 */
final class Preferencias {

    /** Tamanhos de fonte oferecidos: rótulo e multiplicador. */
    enum TamanhoFonte {
        PEQUENA("Pequena", 0.9f), NORMAL("Normal", 1.0f), GRANDE("Grande", 1.15f), MUITO_GRANDE("Muito grande", 1.3f);

        private final String rotulo;
        private final float fator;

        TamanhoFonte(String rotulo, float fator) {
            this.rotulo = rotulo;
            this.fator = fator;
        }

        String rotulo() {
            return rotulo;
        }

        float fator() {
            return fator;
        }

        static TamanhoFonte de(String nome) {
            for (TamanhoFonte t : values()) {
                if (t.name().equals(nome)) {
                    return t;
                }
            }
            return NORMAL;
        }
    }

    /** Cores que o usuário pode escolher para as próprias mensagens. */
    static final Color[] CORES = {
            new Color(0x2563EB), new Color(0x7C3AED), new Color(0xDB2777), new Color(0xDC2626),
            new Color(0xEA580C), new Color(0x16A34A), new Color(0x0891B2), new Color(0x475569)
    };

    private static final String ESCURO = "escuro";
    private static final String FONTE = "fonte";
    private static final String COMPACTO = "compacto";
    private static final String SOM = "som";
    private static final String COR = "cor";
    private static final String MODO_UCI = "modoUci";
    private static final String SILENCIADAS = "silenciadas";

    private final Preferences no;

    Preferencias(Preferences no) {
        this.no = no;
    }

    boolean escuro() {
        return lerBoolean(ESCURO, false);
    }

    TamanhoFonte fonte() {
        return TamanhoFonte.de(ler(FONTE, TamanhoFonte.NORMAL.name()));
    }

    boolean compacto() {
        return lerBoolean(COMPACTO, false);
    }

    boolean som() {
        return lerBoolean(SOM, true);
    }

    /** Índice (0..7) da cor escolhida em {@link #CORES}; -1 para o azul padrão. */
    int indiceDaCor() {
        try {
            int i = Integer.parseInt(ler(COR, "-1"));
            return i >= 0 && i < CORES.length ? i : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Cor própria escolhida, ou {@code null} para o padrão. */
    Color cor() {
        int i = indiceDaCor();
        return i < 0 ? null : CORES[i];
    }

    /** Nome do modo da UCI ("padrao", "formal"...). */
    String modoUci() {
        return ler(MODO_UCI, "padrao");
    }

    Set<String> silenciadas() {
        String bruto = ler(SILENCIADAS, "");
        Set<String> conjunto = new LinkedHashSet<>();
        for (String s : bruto.split(",")) {
            if (!s.isBlank()) {
                conjunto.add(s.trim());
            }
        }
        return conjunto;
    }

    boolean estaSilenciada(Conversa conversa) {
        return silenciadas().contains(conversa.toString());
    }

    void definirEscuro(boolean valor) {
        gravar(ESCURO, String.valueOf(valor));
    }

    void definirFonte(TamanhoFonte tamanho) {
        gravar(FONTE, tamanho.name());
    }

    void definirCompacto(boolean valor) {
        gravar(COMPACTO, String.valueOf(valor));
    }

    void definirSom(boolean valor) {
        gravar(SOM, String.valueOf(valor));
    }

    void definirCor(int indice) {
        gravar(COR, String.valueOf(indice >= 0 && indice < CORES.length ? indice : -1));
    }

    void definirModoUci(String modo) {
        gravar(MODO_UCI, modo == null || modo.isBlank() ? "padrao" : modo);
    }

    /** @return {@code true} se agora está silenciada */
    boolean alternarSilencio(Conversa conversa) {
        Set<String> atual = silenciadas();
        boolean agoraSilenciada = atual.add(conversa.toString());
        if (!agoraSilenciada) {
            atual.remove(conversa.toString());
        }
        gravar(SILENCIADAS, String.join(",", atual));
        return agoraSilenciada;
    }

    /** Aplica aparência (tema, fonte, compacto, cor) ao {@link Tema}. */
    void aplicarAoTema(String meuNome) {
        Tema.aplicar(escuro(), fonte().fator(), compacto());
        Tema.definirIdentidade(meuNome, cor());
    }

    private String ler(String chave, String padrao) {
        try {
            return no.get(chave, padrao);
        } catch (RuntimeException e) {
            return padrao; // preferências são só uma comodidade
        }
    }

    private boolean lerBoolean(String chave, boolean padrao) {
        return Boolean.parseBoolean(ler(chave, String.valueOf(padrao)));
    }

    private void gravar(String chave, String valor) {
        try {
            no.put(chave, valor);
        } catch (RuntimeException e) {
            // sem permissão de gravar: a preferência vale só nesta execução
        }
    }
}
