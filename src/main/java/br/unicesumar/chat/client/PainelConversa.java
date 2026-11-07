package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Usuario;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Conteúdo de UMA conversa (uma aba): balões de mensagem com avatar, no estilo dos aplicativos de
 * chat modernos. As mensagens de quem usa este programa aparecem à direita, em azul; as dos outros
 * à esquerda, com avatar e nome colorido; a UCI tem balão roxo com selo "IA". Avisos do sistema
 * aparecem centralizados.
 *
 * <p>Além de desenhar, o painel guarda o mesmo conteúdo como texto simples ({@link #conteudo()}),
 * usado pelos testes e útil para depuração.</p>
 */
final class PainelConversa extends JScrollPane {

    private static final long serialVersionUID = 1L;

    private final Feed feed = new Feed();
    private final StringBuilder texto = new StringBuilder();
    private String ultimoRemetente; // para agrupar mensagens seguidas da mesma pessoa

    PainelConversa() {
        super(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        setViewportView(feed);
        getViewport().setBackground(Tema.FUNDO_APP);
        Componentes.estilizarRolagem(this);
    }

    // ------------------------------------------------------------------------------------
    // API (mesma de antes, mais a informação de quem enviou)
    // ------------------------------------------------------------------------------------

    void adicionarMensagem(String hora, String remetente, String conteudo) {
        adicionarMensagem(hora, remetente, conteudo, false);
    }

    /** @param propria {@code true} se quem enviou é o usuário deste programa (balão à direita) */
    void adicionarMensagem(String hora, String remetente, String conteudo, boolean propria) {
        boolean primeira = ultimoRemetente == null || !ultimoRemetente.equalsIgnoreCase(remetente);
        ultimoRemetente = remetente;
        texto.append('[').append(hora).append("] ").append(remetente).append(": ").append(conteudo).append('\n');
        anexar(new Balao(hora, remetente, conteudo, propria, primeira));
    }

    /** Aviso do sistema (entrou, saiu etc.): etiqueta cinza centralizada. */
    void adicionarAviso(String aviso) {
        registrar(aviso);
        anexar(new Aviso(aviso, Aviso.Tipo.AVISO));
    }

    /** Cabeçalho de histórico: texto centralizado entre duas linhas. */
    void adicionarCabecalho(String cabecalho) {
        registrar("— " + cabecalho + " —");
        anexar(new Aviso(cabecalho, Aviso.Tipo.CABECALHO));
    }

    void adicionarErro(String erro) {
        registrar(erro);
        anexar(new Aviso(erro, Aviso.Tipo.ERRO));
    }

    /** Resposta de um comando (ex.: /ajuda): cartão branco à esquerda. */
    void adicionarTexto(String linha) {
        registrar(linha);
        anexar(new Aviso(linha, Aviso.Tipo.TEXTO));
    }

    String conteudo() {
        return texto.toString();
    }

    void limpar() {
        feed.removeAll();
        texto.setLength(0);
        ultimoRemetente = null;
        Tema.repintar(feed);
    }

    /** A mesma pessoa sempre aparece com a mesma cor; a UCI tem uma cor própria. */
    static Color corDoNome(String nome) {
        return Tema.corDaPessoa(nome);
    }

    // ------------------------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------------------------

    private void registrar(String linha) {
        texto.append(linha).append('\n');
        ultimoRemetente = null; // qualquer coisa que não seja mensagem quebra o agrupamento
    }

    private void anexar(Linha linha) {
        JScrollBar barra = getVerticalScrollBar();
        boolean noFim = barra.getValue() + barra.getVisibleAmount() >= barra.getMaximum() - 80;
        feed.add(linha);
        feed.revalidate();
        feed.repaint();
        if (noFim) {
            SwingUtilities.invokeLater(() -> barra.setValue(barra.getMaximum()));
        }
    }

    // ------------------------------------------------------------------------------------
    // Contêiner das linhas: ocupa a largura da janela e empilha as linhas
    // ------------------------------------------------------------------------------------

    private abstract static class Linha extends JComponent {
        private static final long serialVersionUID = 1L;

        /** Altura que a linha precisa quando tem {@code largura} pixels disponíveis. */
        abstract int alturaPara(int largura);
    }

    private static final class Feed extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        Feed() {
            super(null);
            setLayout(new Empilhador());
            setBackground(Tema.FUNDO_APP);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(400, 300);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(40, (int) (visible.height * 0.9));
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class Empilhador implements LayoutManager {
        private static final int TOPO = 12;
        private static final int BASE = 14;

        @Override
        public void addLayoutComponent(String nome, Component c) {
        }

        @Override
        public void removeLayoutComponent(Component c) {
        }

        @Override
        public Dimension preferredLayoutSize(Container pai) {
            int w = pai.getWidth() > 0 ? pai.getWidth() : 480;
            int h = TOPO + BASE;
            for (Component c : pai.getComponents()) {
                h += ((Linha) c).alturaPara(w);
            }
            return new Dimension(w, h);
        }

        @Override
        public Dimension minimumLayoutSize(Container pai) {
            return new Dimension(200, 50);
        }

        @Override
        public void layoutContainer(Container pai) {
            int w = pai.getWidth();
            int y = TOPO;
            for (Component c : pai.getComponents()) {
                int h = ((Linha) c).alturaPara(w);
                c.setBounds(0, y, w, h);
                y += h;
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Balão de mensagem
    // ------------------------------------------------------------------------------------

    private static final class Balao extends Linha {
        private static final long serialVersionUID = 1L;

        private static final int MARGEM = 18;
        private static final int AVATAR = 36;
        private static final int ESPACO = 10;
        private static final int PAD_H = 14;
        private final int padV = Tema.compacto() ? 5 : 9;
        private static final float RAIO = 18f;
        private static final float RAIO_BICO = 5f;
        private final int ALTURA_NOME = Math.round(19 * Tema.escala());
        private final int ALTURA_HORA = Math.round(15 * Tema.escala());

        private final String hora;
        private final String remetente;
        private final String conteudo;
        private final boolean propria;
        private final boolean primeira;
        private final boolean uci;
        private final Font fonteTexto = Tema.fonte(Font.PLAIN, 14.5f);
        private final Font fonteNome = Tema.fonte(Font.BOLD, 12.5f);
        private final Font fonteHora = Tema.fonte(Font.PLAIN, 11f);
        private final Font fonteSelo = Tema.fonte(Font.BOLD, 9.5f);

        // calculados por medir()
        private int larguraMedida = -1;
        private List<String> linhas = List.of();
        private int larguraBalao;
        private int alturaBalao;

        Balao(String hora, String remetente, String conteudo, boolean propria, boolean primeira) {
            this.hora = hora;
            this.remetente = remetente;
            this.conteudo = conteudo;
            this.propria = propria;
            this.primeira = primeira;
            this.uci = !propria && Usuario.reservado(remetente);
            JPopupMenu menu = new JPopupMenu();
            JMenuItem copiar = new JMenuItem("Copiar mensagem");
            copiar.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(conteudo), null));
            menu.add(copiar);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    mostrar(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    mostrar(e);
                }

                private void mostrar(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        menu.show(Balao.this, e.getX(), e.getY());
                    }
                }
            });
        }

        private boolean mostraNome() {
            return primeira && !propria;
        }

        private int folgaSuperior() {
            return primeira ? (Tema.compacto() ? 6 : 12) : (Tema.compacto() ? 1 : 3);
        }

        private int larguraDoSelo() {
            return uci ? Tema.larguraTexto(fonteSelo, "IA") + 12 : 0;
        }

        private void medir(int largura) {
            if (largura == larguraMedida) {
                return;
            }
            larguraMedida = largura;
            int areaTexto = largura - 2 * MARGEM - (propria ? 0 : AVATAR + ESPACO);
            int maxBalao = Math.max(120, Math.min((int) (areaTexto * 0.85), 620));
            linhas = Tema.quebrar(conteudo, fonteTexto, maxBalao - 2 * PAD_H);
            int larguraConteudo = Tema.larguraTexto(fonteHora, hora);
            for (String l : linhas) {
                larguraConteudo = Math.max(larguraConteudo, Tema.larguraTexto(fonteTexto, l));
            }
            if (mostraNome()) {
                larguraConteudo = Math.max(larguraConteudo,
                        Tema.larguraTexto(fonteNome, remetente) + (uci ? larguraDoSelo() + 6 : 0));
            }
            larguraBalao = Math.min(maxBalao, larguraConteudo + 2 * PAD_H + 2);
            alturaBalao = padV + (mostraNome() ? ALTURA_NOME : 0)
                    + linhas.size() * Tema.alturaDeLinha(fonteTexto) + 2 + ALTURA_HORA + padV - 3;
        }

        @Override
        int alturaPara(int largura) {
            medir(Math.max(largura, 1));
            return folgaSuperior() + alturaBalao;
        }

        @Override
        protected void paintComponent(Graphics g) {
            medir(Math.max(getWidth(), 1));
            Graphics2D g2 = Tema.suavizar(g);
            int topo = folgaSuperior();
            int x = propria ? getWidth() - MARGEM - larguraBalao : MARGEM + AVATAR + ESPACO;

            if (!propria && primeira) {
                Tema.avatar(g2, remetente, MARGEM, topo, AVATAR);
            }

            float bico = RAIO_BICO;
            float tl = (!propria && primeira) ? bico : RAIO;
            float tr = (propria && primeira) ? bico : RAIO;
            var forma = Tema.retanguloArredondado(x, topo, larguraBalao, alturaBalao, tl, tr, RAIO, RAIO);

            Color corTexto;
            Color corHora;
            if (propria) {
                g2.setPaint(new GradientPaint(x, topo, Tema.propriaClara(), x, topo + alturaBalao, Tema.propriaEscura()));
                g2.fill(forma);
                corTexto = Color.WHITE;
                corHora = new Color(255, 255, 255, 185);
            } else {
                g2.setColor(uci ? Tema.UCI_CLARO : Tema.BALAO_OUTRO);
                g2.fill(forma);
                g2.setColor(uci ? Tema.UCI_BORDA : Tema.BORDA);
                g2.draw(forma);
                corTexto = Tema.TEXTO;
                corHora = Tema.TEXTO_SUAVE;
            }

            int baseX = x + PAD_H;
            float y = topo + padV;
            if (mostraNome()) {
                g2.setFont(fonteNome);
                g2.setColor(Tema.corDaPessoa(remetente));
                g2.drawString(remetente, baseX, y + Tema.subida(fonteNome));
                if (uci) {
                    desenharSelo(g2, baseX + Tema.larguraTexto(fonteNome, remetente) + 6, (int) y + 1);
                }
                y += ALTURA_NOME;
            }
            g2.setFont(fonteTexto);
            g2.setColor(corTexto);
            int lh = Tema.alturaDeLinha(fonteTexto);
            for (String l : linhas) {
                g2.drawString(l, baseX, y + Tema.subida(fonteTexto));
                y += lh;
            }
            g2.setFont(fonteHora);
            g2.setColor(corHora);
            int larguraHora = Tema.larguraTexto(fonteHora, hora);
            g2.drawString(hora, x + larguraBalao - PAD_H - larguraHora, y + 2 + Tema.subida(fonteHora));
            g2.dispose();
        }

        private void desenharSelo(Graphics2D g2, int x, int y) {
            int w = larguraDoSelo();
            g2.setColor(Tema.UCI);
            g2.fill(new RoundRectangle2D.Float(x, y, w, 14, 7, 7));
            g2.setFont(fonteSelo);
            g2.setColor(Color.WHITE);
            g2.drawString("IA", x + 6, y + 1 + Tema.subida(fonteSelo) * 0.95f + 1);
        }
    }

    // ------------------------------------------------------------------------------------
    // Avisos, cabeçalhos, erros e respostas de comandos
    // ------------------------------------------------------------------------------------

    private static final class Aviso extends Linha {
        private static final long serialVersionUID = 1L;

        enum Tipo { AVISO, CABECALHO, ERRO, TEXTO }

        private static final int MARGEM = 18;

        private final String conteudo;
        private final Tipo tipo;
        private final Font fonte;

        private int larguraMedida = -1;
        private List<String> linhas = List.of();
        private int larguraCaixa;
        private int alturaCaixa;

        Aviso(String conteudo, Tipo tipo) {
            this.conteudo = conteudo;
            this.tipo = tipo;
            this.fonte = switch (tipo) {
                case AVISO -> Tema.fonte(Font.PLAIN, 12f);
                case CABECALHO -> Tema.fonte(Font.BOLD, 11.5f);
                case ERRO -> Tema.fonte(Font.PLAIN, 12.5f);
                case TEXTO -> Tema.fonte(Font.PLAIN, 13f);
            };
        }

        private int padH() {
            return tipo == Tipo.TEXTO ? 14 : 12;
        }

        private int padV() {
            return tipo == Tipo.TEXTO ? 9 : 5;
        }

        private int folga() {
            return tipo == Tipo.CABECALHO ? 8 : 6;
        }

        private void medir(int largura) {
            if (largura == larguraMedida) {
                return;
            }
            larguraMedida = largura;
            int max = Math.max(120, Math.min((int) ((largura - 2 * MARGEM) * (tipo == Tipo.TEXTO ? 0.85 : 0.8)), 640));
            linhas = Tema.quebrar(conteudo, fonte, max - 2 * padH());
            int w = 0;
            for (String l : linhas) {
                w = Math.max(w, Tema.larguraTexto(fonte, l));
            }
            larguraCaixa = w + 2 * padH();
            alturaCaixa = linhas.size() * Tema.alturaDeLinha(fonte) + 2 * padV();
        }

        @Override
        int alturaPara(int largura) {
            medir(Math.max(largura, 1));
            return alturaCaixa + 2 * folga();
        }

        @Override
        protected void paintComponent(Graphics g) {
            medir(Math.max(getWidth(), 1));
            Graphics2D g2 = Tema.suavizar(g);
            int y = folga();
            int lh = Tema.alturaDeLinha(fonte);
            g2.setFont(fonte);

            if (tipo == Tipo.CABECALHO) {
                String t = linhas.get(0);
                int w = Tema.larguraTexto(fonte, t);
                int cx = getWidth() / 2;
                int cy = y + alturaCaixa / 2;
                g2.setColor(Tema.BORDA);
                g2.drawLine(MARGEM, cy, Math.max(MARGEM, cx - w / 2 - 12), cy);
                g2.drawLine(Math.min(getWidth() - MARGEM, cx + w / 2 + 12), cy, getWidth() - MARGEM, cy);
                g2.setColor(Tema.TEXTO_SUAVE);
                g2.drawString(t, cx - w / 2f, y + padV() + Tema.subida(fonte));
                g2.dispose();
                return;
            }

            int x = tipo == Tipo.TEXTO ? MARGEM : (getWidth() - larguraCaixa) / 2;
            Color fundo;
            Color texto;
            Color borda = null;
            switch (tipo) {
                case ERRO -> {
                    fundo = Tema.ERRO_CLARO;
                    texto = Tema.ERRO_TEXTO;
                }
                case TEXTO -> {
                    fundo = Tema.BALAO_OUTRO;
                    texto = Tema.TEXTO;
                    borda = Tema.BORDA;
                }
                default -> {
                    fundo = Tema.AVISO_FUNDO;
                    texto = Tema.TEXTO_SUAVE;
                }
            }
            RoundRectangle2D.Float forma = new RoundRectangle2D.Float(x, y, larguraCaixa, alturaCaixa,
                    tipo == Tipo.TEXTO ? 12 : alturaCaixa, tipo == Tipo.TEXTO ? 12 : alturaCaixa);
            g2.setColor(fundo);
            g2.fill(forma);
            if (borda != null) {
                g2.setColor(borda);
                g2.draw(forma);
            }
            g2.setColor(texto);
            float ty = y + padV() + Tema.subida(fonte);
            for (String l : linhas) {
                float tx = tipo == Tipo.TEXTO ? x + padH() : x + (larguraCaixa - Tema.larguraTexto(fonte, l)) / 2f;
                g2.drawString(l, tx, ty);
                ty += lh;
            }
            g2.dispose();
        }
    }
}
