package br.unicesumar.chat.client;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Identidade visual do chat: paleta, fontes e pequenos desenhos (avatar, ícone, balões)
 * usados por todas as telas, para que tudo tenha a mesma aparência.
 */
final class Tema {

    // ---- paleta: claro (padrão) ou escuro; azul como cor principal e roxo para a UCI ----
    // Os valores são trocados por aplicar(...). Ficam em maiúsculas por serem "constantes de tema"
    // lidas por todas as telas; depois de trocar o tema, a janela monta as telas de novo.
    static Color FUNDO_APP;
    static Color SUPERFICIE;
    static Color BORDA;
    static Color TEXTO;
    static Color TEXTO_SUAVE;
    static Color PRIMARIA;
    static Color PRIMARIA_ESCURA;
    static Color PRIMARIA_CLARA;
    static Color PRIMARIA_TEXTO;
    static Color ERRO;
    static Color ERRO_CLARO;
    static Color ERRO_TEXTO;
    static Color SUCESSO;
    static Color UCI;
    static Color UCI_CLARO;
    static Color UCI_BORDA;
    static Color BALAO_OUTRO;
    static Color AVISO_FUNDO;
    static Color CAMPO_FUNDO;
    static Color DICA;
    static Color BOTAO_SOBRE;
    static Color BOTAO_APERTADO;
    static Color PERIGO_BORDA;
    static Color PERIGO_SOBRE;
    static Color ROTULO_FORTE;
    static Color ROLAGEM;
    static Color ROLAGEM_SOBRE;
    static Color INICIO_A;
    static Color INICIO_B;

    /** Aparência atual (lida pelas telas ao se montarem). */
    private static boolean escuro;
    private static boolean compacto;
    private static float escala = 1f;

    static {
        aplicar(false, 1f, false);
    }

    /**
     * Define a aparência de todo o aplicativo.
     *
     * @param modoEscuro paleta escura
     * @param fatorFonte multiplicador dos tamanhos de fonte (limitado a 0,8 – 1,6)
     * @param modoCompacto espaçamentos menores nas mensagens
     */
    static synchronized void aplicar(boolean modoEscuro, float fatorFonte, boolean modoCompacto) {
        escuro = modoEscuro;
        compacto = modoCompacto;
        escala = Math.max(0.8f, Math.min(1.6f, fatorFonte));
        if (!escuro) {
            FUNDO_APP = new Color(0xF1F5F9);
            SUPERFICIE = Color.WHITE;
            BORDA = new Color(0xE2E8F0);
            TEXTO = new Color(0x0F172A);
            TEXTO_SUAVE = new Color(0x64748B);
            PRIMARIA = new Color(0x2563EB);
            PRIMARIA_ESCURA = new Color(0x1D4ED8);
            PRIMARIA_CLARA = new Color(0xDBEAFE);
            PRIMARIA_TEXTO = new Color(0x1D4ED8);
            ERRO = new Color(0xDC2626);
            ERRO_CLARO = new Color(0xFEE2E2);
            ERRO_TEXTO = new Color(0x991B1B);
            SUCESSO = new Color(0x16A34A);
            UCI = new Color(0x7C3AED);
            UCI_CLARO = new Color(0xF5F3FF);
            UCI_BORDA = new Color(0xDDD6FE);
            BALAO_OUTRO = Color.WHITE;
            AVISO_FUNDO = new Color(0xE2E8F0);
            CAMPO_FUNDO = Color.WHITE;
            DICA = new Color(0x94A3B8);
            BOTAO_SOBRE = new Color(0xF1F5F9);
            BOTAO_APERTADO = new Color(0xE2E8F0);
            PERIGO_BORDA = new Color(0xFECACA);
            PERIGO_SOBRE = new Color(0xFEE2E2);
            ROTULO_FORTE = new Color(0x334155);
            ROLAGEM = new Color(0xCBD5E1);
            ROLAGEM_SOBRE = new Color(0x94A3B8);
            INICIO_A = new Color(0xE0E7FF);
            INICIO_B = new Color(0xF3E8FF);
        } else {
            FUNDO_APP = new Color(0x0B1220);
            SUPERFICIE = new Color(0x111A2E);
            BORDA = new Color(0x26334A);
            TEXTO = new Color(0xE2E8F0);
            TEXTO_SUAVE = new Color(0x94A3B8);
            PRIMARIA = new Color(0x3B82F6);
            PRIMARIA_ESCURA = new Color(0x2563EB);
            PRIMARIA_CLARA = new Color(0x1E3A5F);
            PRIMARIA_TEXTO = new Color(0x93C5FD);
            ERRO = new Color(0xF87171);
            ERRO_CLARO = new Color(0x3F1D24);
            ERRO_TEXTO = new Color(0xFCA5A5);
            SUCESSO = new Color(0x22C55E);
            UCI = new Color(0xA78BFA);
            UCI_CLARO = new Color(0x241B3D);
            UCI_BORDA = new Color(0x4C3A85);
            BALAO_OUTRO = new Color(0x1B2740);
            AVISO_FUNDO = new Color(0x1E293B);
            CAMPO_FUNDO = new Color(0x0B1220);
            DICA = new Color(0x64748B);
            BOTAO_SOBRE = new Color(0x1E293B);
            BOTAO_APERTADO = new Color(0x26334A);
            PERIGO_BORDA = new Color(0x7F1D1D);
            PERIGO_SOBRE = new Color(0x3F1D24);
            ROTULO_FORTE = new Color(0xCBD5E1);
            ROLAGEM = new Color(0x334155);
            ROLAGEM_SOBRE = new Color(0x475569);
            INICIO_A = new Color(0x0B1220);
            INICIO_B = new Color(0x1E1B4B);
        }
    }

    /** Cor escolhida pelo usuário para as próprias mensagens (null = azul padrão) e o nome dele. */
    private static volatile Color corPropria;
    private static volatile String meuNome = "";

    static void definirIdentidade(String nome, Color cor) {
        meuNome = nome == null ? "" : nome;
        corPropria = cor;
    }

    /** Topo do degradê do balão das mensagens próprias. */
    static Color propriaClara() {
        Color c = corPropria;
        return c == null ? new Color(0x3B82F6) : misturar(c, Color.WHITE, 0.12f);
    }

    /** Base do degradê do balão das mensagens próprias. */
    static Color propriaEscura() {
        Color c = corPropria;
        return c == null ? PRIMARIA_ESCURA : misturar(c, Color.BLACK, 0.18f);
    }

    static boolean escuro() {
        return escuro;
    }

    static boolean compacto() {
        return compacto;
    }

    static float escala() {
        return escala;
    }

    private static final Color[] CORES_PESSOAS = {
            new Color(0x2563EB), new Color(0x059669), new Color(0xD97706), new Color(0xDB2777),
            new Color(0x0891B2), new Color(0x65A30D), new Color(0xDC2626), new Color(0x0F766E)
    };

    /** Mede e desenha texto com as mesmas configurações, para as quebras de linha baterem com o que aparece. */
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final String FAMILIA = escolherFamilia();

    private Tema() {
    }

    // ------------------------------------------------------------------------------------
    // Fontes e cores
    // ------------------------------------------------------------------------------------

    static Font fonte(int estilo, float tamanho) {
        return new Font(FAMILIA, estilo, 1).deriveFont(estilo, tamanho * escala);
    }

    private static String escolherFamilia() {
        Set<String> disponiveis = Set.copyOf(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Noto Sans", "Ubuntu",
                "DejaVu Sans"}) {
            if (disponiveis.contains(f)) {
                return f;
            }
        }
        return Font.SANS_SERIF;
    }

    /** A mesma pessoa sempre tem a mesma cor; a UCI tem a sua. */
    static Color corDaPessoa(String nome) {
        if (br.unicesumar.chat.model.Usuario.reservado(nome)) {
            return UCI;
        }
        Color propria = corPropria;
        if (propria != null && nome.equalsIgnoreCase(meuNome)) {
            return propria;
        }
        return CORES_PESSOAS[Math.floorMod(nome.toLowerCase(Locale.ROOT).hashCode(), CORES_PESSOAS.length)];
    }

    static Color comTransparencia(Color c, int alfa) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alfa);
    }

    static Color misturar(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ------------------------------------------------------------------------------------
    // Desenho
    // ------------------------------------------------------------------------------------

    /** Cópia do Graphics com serrilhado suavizado (bordas e texto). Quem chama deve dar dispose(). */
    static Graphics2D suavizar(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** Retângulo com raio próprio em cada canto (permite o "bico" do balão de conversa). */
    static Shape retanguloArredondado(float x, float y, float w, float h, float tl, float tr, float br, float bl) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(x + tl, y);
        p.lineTo(x + w - tr, y);
        p.quadTo(x + w, y, x + w, y + tr);
        p.lineTo(x + w, y + h - br);
        p.quadTo(x + w, y + h, x + w - br, y + h);
        p.lineTo(x + bl, y + h);
        p.quadTo(x, y + h, x, y + h - bl);
        p.lineTo(x, y + tl);
        p.quadTo(x, y, x + tl, y);
        p.closePath();
        return p;
    }

    /** Sombra suave: várias camadas translúcidas, cada uma um pouco maior. */
    static void sombra(Graphics2D g, float x, float y, float w, float h, float raio, int camadas, int alfa) {
        for (int i = camadas; i >= 1; i--) {
            g.setColor(new Color(15, 23, 42, Math.max(1, alfa / (i + 1))));
            g.fill(new RoundRectangle2D.Float(x - i, y - i + camadas / 2f, w + 2 * i, h + 2 * i, raio + i, raio + i));
        }
    }

    static String iniciais(String nome) {
        if (br.unicesumar.chat.model.Usuario.reservado(nome)) {
            return "UCI";
        }
        String n = nome == null ? "" : nome.trim();
        return n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }

    /** Avatar circular com a inicial do nome (a UCI recebe um degradê roxo). */
    static void avatar(Graphics2D g, String nome, int x, int y, int d) {
        boolean uci = br.unicesumar.chat.model.Usuario.reservado(nome);
        Color base = corDaPessoa(nome);
        g.setPaint(new GradientPaint(x, y, uci ? new Color(0xA78BFA) : misturar(base, Color.WHITE, 0.25f),
                x + d, y + d, uci ? UCI : misturar(base, Color.BLACK, 0.15f)));
        g.fillOval(x, y, d, d);
        String texto = iniciais(nome);
        Font f = fonte(Font.BOLD, uci ? d * 0.30f : d * 0.42f);
        g.setFont(f);
        g.setColor(Color.WHITE);
        LineMetrics lm = f.getLineMetrics(texto, FRC);
        float tx = x + (d - larguraTexto(f, texto)) / 2f;
        float ty = y + (d + lm.getAscent() - lm.getDescent()) / 2f;
        g.drawString(texto, tx, ty);
    }

    /** Imagem do ícone do aplicativo (balão de conversa branco sobre degradê azul-roxo). */
    static BufferedImage icone(int tamanho) {
        BufferedImage img = new BufferedImage(tamanho, tamanho, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = suavizar(img.getGraphics());
        desenharIcone(g, 0, 0, tamanho);
        g.dispose();
        return img;
    }

    static void desenharIcone(Graphics2D g, int x, int y, int d) {
        g.setPaint(new GradientPaint(x, y, new Color(0x3B82F6), x + d, y + d, new Color(0x7C3AED)));
        g.fill(new RoundRectangle2D.Float(x, y, d, d, d * 0.32f, d * 0.32f));
        float bw = d * 0.56f;
        float bh = d * 0.38f;
        float bx = x + (d - bw) / 2f;
        float by = y + d * 0.26f;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh * 0.5f, bh * 0.5f));
        Path2D.Float bico = new Path2D.Float();
        bico.moveTo(bx + bw * 0.22f, by + bh - 1);
        bico.lineTo(bx + bw * 0.16f, by + bh + d * 0.12f);
        bico.lineTo(bx + bw * 0.42f, by + bh - 1);
        bico.closePath();
        g.fill(bico);
        g.setColor(new Color(0x2563EB));
        float r = d * 0.045f;
        for (int i = 0; i < 3; i++) {
            g.fill(new java.awt.geom.Ellipse2D.Float(bx + bw * (0.30f + i * 0.20f) - r, by + bh / 2f - r, 2 * r, 2 * r));
        }
    }

    // ------------------------------------------------------------------------------------
    // Texto: medição e quebra de linhas
    // ------------------------------------------------------------------------------------

    static int larguraTexto(Font f, String texto) {
        return (int) Math.ceil(f.getStringBounds(texto, FRC).getWidth());
    }

    static int alturaDeLinha(Font f) {
        return (int) Math.ceil(f.getLineMetrics("Ag", FRC).getHeight());
    }

    static float subida(Font f) {
        return f.getLineMetrics("Ag", FRC).getAscent();
    }

    /** Quebra o texto em linhas de no máximo {@code largura} pixels (respeita \n e corta palavras gigantes). */
    static List<String> quebrar(String texto, Font f, int largura) {
        List<String> linhas = new ArrayList<>();
        for (String paragrafo : texto.split("\n", -1)) {
            StringBuilder atual = new StringBuilder();
            for (String palavra : paragrafo.split(" ", -1)) {
                String tentativa = atual.length() == 0 ? palavra : atual + " " + palavra;
                if (larguraTexto(f, tentativa) <= largura) {
                    atual = new StringBuilder(tentativa);
                    continue;
                }
                if (atual.length() > 0) {
                    linhas.add(atual.toString());
                    atual = new StringBuilder();
                }
                String resto = palavra;
                while (resto.length() > 1 && larguraTexto(f, resto) > largura) {
                    int corte = resto.offsetByCodePoints(0, 1);
                    while (corte < resto.length()) {
                        int proximo = resto.offsetByCodePoints(corte, 1);
                        if (larguraTexto(f, resto.substring(0, proximo)) > largura) {
                            break;
                        }
                        corte = proximo;
                    }
                    linhas.add(resto.substring(0, corte));
                    resto = resto.substring(corte);
                }
                atual = new StringBuilder(resto);
            }
            linhas.add(atual.toString());
        }
        return linhas;
    }

    /** Pede repintura sem erro quando o componente ainda não foi exibido. */
    static void repintar(JComponent c) {
        if (c != null) {
            c.revalidate();
            c.repaint();
        }
    }
}
