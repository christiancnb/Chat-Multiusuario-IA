package br.unicesumar.chat.client;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Tela inicial: servidor, porta e nome, com o botão "Entrar no chat".
 * Se já houver uma conversa em andamento, mostra também "Voltar ao chat".
 * É só visual: quem reage aos botões é {@link AplicacaoChat}.
 */
public class TelaInicio extends JPanel {

    private final Componentes.Campo campoServidor = new Componentes.Campo("localhost", 22);
    private final Componentes.Campo campoPorta = new Componentes.Campo("5000", 22);
    private final Componentes.Campo campoNome = new Componentes.Campo("Como você quer ser chamado?", 22);
    private final JButton botaoEntrar = new Componentes.Botao("Entrar no chat", Componentes.Estilo.PRIMARIO);
    private final JButton botaoVoltar = new Componentes.Botao("Voltar ao chat", Componentes.Estilo.SECUNDARIO);
    private final JLabel rotuloStatus = new JLabel(" ");

    public TelaInicio() {
        setLayout(new GridBagLayout());
        setOpaque(false);

        JPanel cartao = new Cartao();
        cartao.setLayout(new GridBagLayout());
        cartao.setBorder(BorderFactory.createEmptyBorder(34, 40, 34, 40));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        JPanel marca = new JPanel(new java.awt.BorderLayout(14, 0));
        marca.setOpaque(false);
        marca.add(new Componentes.Logo(52), java.awt.BorderLayout.WEST);
        JPanel textos = new JPanel(new GridBagLayout());
        textos.setOpaque(false);
        JLabel titulo = new JLabel("Chat Multiusuário");
        titulo.setFont(Tema.fonte(Font.BOLD, 24f));
        titulo.setForeground(Tema.TEXTO);
        JLabel subtitulo = new JLabel("Grupos, conversas privadas e a assistente UCI");
        subtitulo.setFont(Tema.fonte(Font.PLAIN, 13f));
        subtitulo.setForeground(Tema.TEXTO_SUAVE);
        GridBagConstraints ct = new GridBagConstraints();
        ct.gridx = 0;
        ct.anchor = GridBagConstraints.WEST;
        textos.add(titulo, ct);
        textos.add(subtitulo, ct);
        marca.add(textos, java.awt.BorderLayout.CENTER);
        adicionar(cartao, marca, c, 0);

        adicionar(cartao, rotulo("Servidor"), c, 26);
        adicionar(cartao, campoServidor, c, 6);
        adicionar(cartao, rotulo("Porta"), c, 14);
        adicionar(cartao, campoPorta, c, 6);
        adicionar(cartao, rotulo("Seu nome (2 a 20 caracteres, sem espaços)"), c, 14);
        adicionar(cartao, campoNome, c, 6);

        botaoEntrar.setFont(Tema.fonte(Font.BOLD, 15f));
        botaoEntrar.setPreferredSize(new Dimension(100, 46));
        adicionar(cartao, botaoEntrar, c, 24);

        botaoVoltar.setPreferredSize(new Dimension(100, 42));
        botaoVoltar.setVisible(false);
        adicionar(cartao, botaoVoltar, c, 10);

        rotuloStatus.setHorizontalAlignment(JLabel.CENTER);
        rotuloStatus.setFont(Tema.fonte(Font.PLAIN, 13f));
        adicionar(cartao, rotuloStatus, c, 14);

        add(cartao, new GridBagConstraints());

        // Enter em qualquer campo equivale a clicar em "Entrar no chat".
        campoServidor.addActionListener(e -> botaoEntrar.doClick());
        campoPorta.addActionListener(e -> botaoEntrar.doClick());
        campoNome.addActionListener(e -> botaoEntrar.doClick());
    }

    /** Fundo em degradê suave com duas formas decorativas translúcidas. */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Tema.suavizar(g);
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, Tema.INICIO_A, w, h, Tema.INICIO_B));
        g2.fillRect(0, 0, w, h);
        g2.setColor(new Color(0x3B82F6 & 0xFFFFFF | 0x26000000, true));
        g2.fill(new Ellipse2D.Float(-w * 0.12f, h * 0.55f, w * 0.45f, w * 0.45f));
        g2.setColor(new Color(0x7C3AED & 0xFFFFFF | 0x22000000, true));
        g2.fill(new Ellipse2D.Float(w * 0.72f, -h * 0.25f, w * 0.40f, w * 0.40f));
        g2.dispose();
    }

    /** Cartão branco arredondado com sombra suave. */
    private static final class Cartao extends JPanel {
        private static final long serialVersionUID = 1L;

        Cartao() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            int m = 10;
            float w = getWidth() - 2 * m;
            float h = getHeight() - 2 * m - 4;
            Tema.sombra(g2, m, m, w, h, 24, 8, 16);
            g2.setColor(Tema.SUPERFICIE);
            g2.fill(new RoundRectangle2D.Float(m, m, w, h, 24, 24));
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 440) + 20, d.height + 24);
        }
    }

    // ---- API usada pelo controlador ----

    public String servidor() {
        return campoServidor.getText();
    }

    public String porta() {
        return campoPorta.getText();
    }

    public String nome() {
        return campoNome.getText();
    }

    public void definirDados(String servidor, String porta, String nome) {
        if (servidor != null && !servidor.isBlank()) campoServidor.setText(servidor);
        if (porta != null && !porta.isBlank()) campoPorta.setText(porta);
        if (nome != null) campoNome.setText(nome);
    }

    public void aoEntrar(Runnable acao) {
        botaoEntrar.addActionListener(e -> acao.run());
    }

    public void aoVoltar(Runnable acao) {
        botaoVoltar.addActionListener(e -> acao.run());
    }

    /** Bloqueia os campos enquanto a conexão está sendo estabelecida. */
    public void definirOcupado(boolean ocupado) {
        campoServidor.setEnabled(!ocupado);
        campoPorta.setEnabled(!ocupado);
        campoNome.setEnabled(!ocupado);
        botaoEntrar.setEnabled(!ocupado);
        botaoVoltar.setEnabled(!ocupado);
        botaoEntrar.setText(ocupado ? "Conectando..." : "Entrar no chat");
        if (ocupado) {
            mostrarInfo("Conectando ao servidor...");
        }
    }

    /** Mostra (ou esconde, com {@code null}) o botão para retomar a conversa em andamento. */
    public void mostrarConversaEmAndamento(String nomeConectado) {
        botaoVoltar.setVisible(nomeConectado != null);
        if (nomeConectado != null) {
            botaoVoltar.setText("Voltar ao chat (conectado como " + nomeConectado + ")");
        }
    }

    public void mostrarErro(String mensagem) {
        definirStatus(mensagem, Tema.ERRO);
    }

    public void mostrarInfo(String mensagem) {
        definirStatus(mensagem, Tema.SUCESSO);
    }

    public void limparStatus() {
        definirStatus(" ", Tema.TEXTO_SUAVE);
    }

    public void focarNome() {
        campoNome.requestFocusInWindow();
    }

    // ---- acesso para testes na mesma pacote ----

    JButton botaoEntrar() {
        return botaoEntrar;
    }

    JButton botaoVoltar() {
        return botaoVoltar;
    }

    JTextField campoNome() {
        return campoNome;
    }

    JTextField campoServidor() {
        return campoServidor;
    }

    JTextField campoPorta() {
        return campoPorta;
    }

    JLabel rotuloStatus() {
        return rotuloStatus;
    }

    // ---- utilidades ----

    private void definirStatus(String mensagem, Color cor) {
        rotuloStatus.setForeground(cor);
        rotuloStatus.setText(mensagem == null || mensagem.isBlank() ? " " : mensagem);
    }

    private static JLabel rotulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(Tema.fonte(Font.BOLD, 12f));
        l.setForeground(Tema.ROTULO_FORTE);
        return l;
    }

    private static void adicionar(JPanel painel, Component componente, GridBagConstraints c, int espacoAntes) {
        c.insets = new Insets(espacoAntes, 0, 0, 0);
        painel.add(componente, c);
        c.gridy++;
    }
}
