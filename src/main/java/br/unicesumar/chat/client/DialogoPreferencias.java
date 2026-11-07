package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;

/**
 * Diálogo "Aparência e avisos": tema claro/escuro, tamanho da fonte, modo compacto, som, cor das
 * próprias mensagens e jeito de responder da UCI. Grava direto em {@link Preferencias} e avisa
 * quem chamou (que remonta a janela) ao aplicar. Não tem regra de negócio: é só formulário.
 */
final class DialogoPreferencias extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JRadioButton claro = new JRadioButton("Claro");
    private final JRadioButton escuro = new JRadioButton("Escuro");
    private final JComboBox<Preferencias.TamanhoFonte> fonte = new JComboBox<>(Preferencias.TamanhoFonte.values());
    private final JCheckBox compacto = new JCheckBox("Modo compacto (mensagens mais juntas)");
    private final JCheckBox som = new JCheckBox("Tocar um som quando chegar mensagem em outra aba ou com a janela em segundo plano");
    private final JComboBox<String> modoUci = new JComboBox<>(
            new String[]{"padrao", "formal", "descontraida", "professora"});
    private final JToggleButton[] cores = new JToggleButton[Preferencias.CORES.length + 1];

    private DialogoPreferencias(Window pai, Preferencias prefs, Runnable aoAplicar) {
        super(pai, "Aparência e avisos", ModalityType.APPLICATION_MODAL);
        JPanel conteudo = new JPanel();
        conteudo.setLayout(new BoxLayout(conteudo, BoxLayout.Y_AXIS));
        conteudo.setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
        conteudo.setBackground(Tema.SUPERFICIE);

        ButtonGroup tema = new ButtonGroup();
        tema.add(claro);
        tema.add(escuro);
        (prefs.escuro() ? escuro : claro).setSelected(true);
        fonte.setSelectedItem(prefs.fonte());
        compacto.setSelected(prefs.compacto());
        som.setSelected(prefs.som());
        modoUci.setSelectedItem(prefs.modoUci());

        conteudo.add(secao("Tema"));
        conteudo.add(linha(claro, escuro));
        conteudo.add(secao("Tamanho da fonte"));
        conteudo.add(linha(fonte));
        conteudo.add(secao("Mensagens"));
        conteudo.add(linha(compacto));
        conteudo.add(secao("Cor das minhas mensagens"));
        conteudo.add(linhaDeCores(prefs.indiceDaCor()));
        conteudo.add(secao("Avisos"));
        conteudo.add(linha(som));
        conteudo.add(secao("Jeito de a UCI responder para mim"));
        conteudo.add(linha(modoUci));

        for (JComponent c : new JComponent[]{claro, escuro, compacto, som}) {
            c.setOpaque(false);
            c.setForeground(Tema.TEXTO);
        }

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botoes.setOpaque(false);
        botoes.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        var cancelar = new Componentes.Botao("Cancelar", Componentes.Estilo.SECUNDARIO);
        var aplicar = new Componentes.Botao("Aplicar", Componentes.Estilo.PRIMARIO);
        cancelar.addActionListener(e -> dispose());
        aplicar.addActionListener(e -> {
            gravar(prefs);
            dispose();
            aoAplicar.run();
        });
        botoes.add(cancelar);
        botoes.add(aplicar);
        conteudo.add(botoes);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(Tema.SUPERFICIE);
        getContentPane().add(conteudo, BorderLayout.CENTER);
        getRootPane().setDefaultButton(aplicar);
        pack();
        setMinimumSize(new Dimension(Math.max(getWidth(), 460), getHeight()));
        setLocationRelativeTo(pai);
    }

    /** Abre o diálogo (bloqueia até fechar). {@code aoAplicar} roda só se o usuário clicar em Aplicar. */
    static void abrir(Component pai, Preferencias prefs, Runnable aoAplicar) {
        Window janela = pai == null ? null : SwingUtilities.getWindowAncestor(pai);
        new DialogoPreferencias(janela, prefs, aoAplicar).setVisible(true);
    }

    private void gravar(Preferencias prefs) {
        prefs.definirEscuro(escuro.isSelected());
        prefs.definirFonte((Preferencias.TamanhoFonte) fonte.getSelectedItem());
        prefs.definirCompacto(compacto.isSelected());
        prefs.definirSom(som.isSelected());
        prefs.definirModoUci(String.valueOf(modoUci.getSelectedItem()));
        int escolhida = -1;
        for (int i = 1; i < cores.length; i++) {
            if (cores[i].isSelected()) {
                escolhida = i - 1;
            }
        }
        prefs.definirCor(escolhida);
    }

    private static JLabel secao(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(Tema.fonte(Font.BOLD, 12f));
        l.setForeground(Tema.TEXTO_SUAVE);
        l.setBorder(BorderFactory.createEmptyBorder(12, 0, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel linha(JComponent... componentes) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JComponent c : componentes) {
            p.add(c);
            p.add(Box.createHorizontalStrut(12));
        }
        return p;
    }

    private JPanel linhaDeCores(int indiceAtual) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup grupo = new ButtonGroup();
        for (int i = 0; i < cores.length; i++) {
            Color cor = i == 0 ? Preferencias.CORES[0] : Preferencias.CORES[i - 1];
            cores[i] = new Amostra(cor, i == 0);
            cores[i].setToolTipText(i == 0 ? "Padrão" : "Cor " + i);
            grupo.add(cores[i]);
            p.add(cores[i]);
        }
        cores[indiceAtual + 1].setSelected(true);
        return p;
    }

    /** Círculo colorido selecionável (o primeiro, tracejado, é o padrão). */
    private static final class Amostra extends JToggleButton {
        private static final long serialVersionUID = 1L;

        private final Color cor;
        private final boolean padrao;

        Amostra(Color cor, boolean padrao) {
            this.cor = cor;
            this.padrao = padrao;
            setPreferredSize(new Dimension(30, 30));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            g2.setColor(cor);
            g2.fillOval(4, 4, getWidth() - 8, getHeight() - 8);
            if (padrao) {
                g2.setColor(Color.WHITE);
                g2.setFont(Tema.fonte(Font.BOLD, 10f));
                g2.drawString("A", getWidth() / 2 - 3, getHeight() / 2 + 4);
            }
            if (isSelected()) {
                g2.setColor(Tema.TEXTO);
                g2.setStroke(new java.awt.BasicStroke(2f));
                g2.drawOval(1, 1, getWidth() - 3, getHeight() - 3);
            }
            g2.dispose();
        }
    }
}
