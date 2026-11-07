package br.unicesumar.chat.client;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Componentes Swing desenhados à mão para o visual moderno do chat (cantos arredondados, cores do {@link Tema}). */
final class Componentes {

    private Componentes() {
    }

    // ------------------------------------------------------------------------------------
    // Campo de texto arredondado com texto de dica
    // ------------------------------------------------------------------------------------

    static final class Campo extends JTextField {
        private static final long serialVersionUID = 1L;

        private String dica = "";

        Campo(String dica, int colunas) {
            super(colunas);
            this.dica = dica;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            setFont(Tema.fonte(Font.PLAIN, 14f));
            setForeground(Tema.TEXTO);
            setCaretColor(Tema.PRIMARIA);
            setSelectionColor(Tema.PRIMARIA_CLARA);
            setSelectedTextColor(Tema.TEXTO);
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        public void updateUI() {
            setUI(new BasicTextFieldUI()); // sem o desenho do tema do sistema: quem pinta é este componente
        }

        void definirDica(String novaDica) {
            this.dica = novaDica == null ? "" : novaDica;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(isEnabled() ? Tema.CAMPO_FUNDO : Tema.FUNDO_APP);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            if (isFocusOwner()) {
                g2.setColor(Tema.comTransparencia(Tema.PRIMARIA, 60));
                g2.setStroke(new java.awt.BasicStroke(3f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 14, 14));
            }
            g2.setStroke(new java.awt.BasicStroke(1f));
            g2.setColor(isFocusOwner() ? Tema.PRIMARIA : Tema.BORDA);
            g2.draw(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            if (getText().isEmpty() && !dica.isEmpty()) {
                g2.setFont(getFont());
                g2.setColor(Tema.DICA);
                Insets in = getInsets();
                int y = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
                g2.drawString(dica, in.left, y);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------------------------------
    // Botão arredondado em quatro estilos
    // ------------------------------------------------------------------------------------

    enum Estilo { PRIMARIO, SECUNDARIO, PERIGO, FANTASMA }

    static final class Botao extends JButton {
        private static final long serialVersionUID = 1L;

        private final Estilo estilo;

        Botao(String texto, Estilo estilo) {
            super(texto);
            this.estilo = estilo;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Tema.fonte(Font.BOLD, 13f));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            setForeground(corTexto());
        }

        @Override
        public void updateUI() {
            setUI(new BasicButtonUI());
        }

        private Color corTexto() {
            return switch (estilo) {
                case PRIMARIO -> Color.WHITE;
                case PERIGO -> Tema.ERRO;
                case SECUNDARIO -> Tema.TEXTO;
                case FANTASMA -> Tema.TEXTO_SUAVE;
            };
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            boolean sobre = getModel().isRollover() && isEnabled();
            boolean apertado = getModel().isArmed() && isEnabled();
            Color fundo;
            Color borda = null;
            switch (estilo) {
                case PRIMARIO -> fundo = !isEnabled() ? new Color(0x93C5FD) : apertado ? new Color(0x1E40AF)
                        : sobre ? Tema.PRIMARIA_ESCURA : Tema.PRIMARIA;
                case PERIGO -> {
                    fundo = apertado ? Tema.PERIGO_BORDA : sobre ? Tema.PERIGO_SOBRE : Tema.SUPERFICIE;
                    borda = Tema.PERIGO_BORDA;
                }
                case SECUNDARIO -> {
                    fundo = apertado ? Tema.BOTAO_APERTADO : sobre ? Tema.BOTAO_SOBRE : Tema.SUPERFICIE;
                    borda = Tema.BORDA;
                }
                default -> fundo = apertado ? Tema.BOTAO_APERTADO : sobre ? Tema.BOTAO_SOBRE : new Color(0, 0, 0, 0);
            }
            RoundRectangle2D.Float forma = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(fundo);
            g2.fill(forma);
            if (borda != null) {
                g2.setColor(borda);
                g2.draw(forma);
            }
            g2.dispose();
            setForeground(isEnabled() ? corTexto() : new Color(0xFFFFFF));
            super.paintComponent(g);
        }
    }

    /** Botão circular azul com o avião de papel, usado para enviar. */
    static final class BotaoEnviar extends JButton {
        private static final long serialVersionUID = 1L;

        BotaoEnviar() {
            super("");
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Enviar (Enter)");
            getAccessibleContext().setAccessibleName("Enviar");
            setPreferredSize(new Dimension(44, 44));
        }

        @Override
        public void updateUI() {
            setUI(new BasicButtonUI());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            int d = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;
            boolean sobre = getModel().isRollover() && isEnabled();
            g2.setPaint(new GradientPaint(x, y, sobre ? new Color(0x3B82F6) : new Color(0x4F8DF7),
                    x, y + d, sobre ? Tema.PRIMARIA_ESCURA : Tema.PRIMARIA));
            g2.fillOval(x, y, d, d);
            Path2D.Float aviao = new Path2D.Float();
            float cx = x + d / 2f;
            float cy = y + d / 2f;
            float s = d * 0.26f;
            aviao.moveTo(cx - s, cy - s);
            aviao.lineTo(cx + s * 1.2f, cy);
            aviao.lineTo(cx - s, cy + s);
            aviao.lineTo(cx - s * 0.45f, cy);
            aviao.closePath();
            g2.setColor(Color.WHITE);
            g2.fill(aviao);
            g2.dispose();
        }
    }

    /** Botão "×" pequeno e redondo para fechar abas. */
    static final class Fechar extends JButton {
        private static final long serialVersionUID = 1L;

        Fechar() {
            super("");
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(18, 18));
            getAccessibleContext().setAccessibleName("Fechar aba");
        }

        @Override
        public void updateUI() {
            setUI(new BasicButtonUI());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            int d = Math.min(getWidth(), getHeight());
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;
            if (getModel().isRollover()) {
                g2.setColor(Tema.ROLAGEM);
                g2.fillOval(x, y, d, d);
            }
            g2.setColor(getModel().isRollover() ? Tema.TEXTO : Tema.TEXTO_SUAVE);
            g2.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            float m = d * 0.32f;
            g2.draw(new java.awt.geom.Line2D.Float(x + m, y + m, x + d - m, y + d - m));
            g2.draw(new java.awt.geom.Line2D.Float(x + d - m, y + m, x + m, y + d - m));
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------------------------
    // Abas em formato de pílula
    // ------------------------------------------------------------------------------------

    static void estilizarAbas(JTabbedPane abas) {
        abas.setUI(new UiAbas());
        abas.setOpaque(true);
        abas.setBackground(Tema.SUPERFICIE);
        abas.setFont(Tema.fonte(Font.PLAIN, 13f));
    }

    private static final class UiAbas extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(5, 8, 5, 8);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(8, 12, 8, 12);
            contentBorderInsets = new Insets(0, 0, 0, 0);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                                          boolean isSelected) {
            Graphics2D g2 = Tema.suavizar(g);
            if (isSelected) {
                g2.setColor(Tema.PRIMARIA_CLARA);
                g2.fill(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 4, 16, 16));
            }
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                                      boolean isSelected) {
            // sem borda: a pílula já separa as abas
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                                           Rectangle iconRect, Rectangle textRect, boolean isSelected) {
            // sem contorno pontilhado de foco
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            int y = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
            g.setColor(Tema.BORDA);
            g.drawLine(0, y, tabPane.getWidth(), y);
        }

        @Override
        protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
            return 0;
        }

        @Override
        protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
            return 0;
        }
    }

    // ------------------------------------------------------------------------------------
    // Barra de rolagem fina
    // ------------------------------------------------------------------------------------

    static void estilizarRolagem(JScrollPane painel) {
        for (JScrollBar barra : new JScrollBar[]{painel.getVerticalScrollBar(), painel.getHorizontalScrollBar()}) {
            barra.setUI(new UiBarra());
            barra.setOpaque(false);
            barra.setUnitIncrement(18);
        }
        painel.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        painel.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
        painel.setBorder(null);
    }

    private static final class UiBarra extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return botaoVazio();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return botaoVazio();
        }

        private static JButton botaoVazio() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setBorder(null);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle area) {
            // trilho invisível
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle area) {
            if (area.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = Tema.suavizar(g);
            g2.setColor(isDragging || isThumbRollover() ? Tema.ROLAGEM_SOBRE : Tema.ROLAGEM);
            g2.fill(new RoundRectangle2D.Float(area.x + 2, area.y + 2, area.width - 4, area.height - 4, 8, 8));
            g2.dispose();
        }
    }

    /** Ícone do aplicativo como componente (cabeçalho e tela inicial). */
    static final class Logo extends JComponent {
        private static final long serialVersionUID = 1L;

        Logo(int tamanho) {
            setPreferredSize(new Dimension(tamanho, tamanho));
            setMinimumSize(new Dimension(tamanho, tamanho));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            Tema.desenharIcone(g2, 0, 0, Math.min(getWidth(), getHeight()));
            g2.dispose();
        }
    }

    /** Ajuda a evitar importar AbstractButton só para quem estiliza botões avulsos. */
    static void plano(AbstractButton b) {
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
    }
}
