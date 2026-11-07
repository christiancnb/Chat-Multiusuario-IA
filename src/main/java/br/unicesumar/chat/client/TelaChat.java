package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Usuario;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Tela do chat: barra superior (sessão + "Início" e "Sair do chat"), abas de conversa
 * (Geral, grupos e privadas), lista de quem está online, lista de grupos e campo de envio.
 * Duplo clique em um usuário abre a conversa privada; duplo clique em um grupo abre a aba dele.
 *
 * <p>É só visual: quem envia, conecta e fala com o servidor é {@link AplicacaoChat}. Todos os
 * métodos devem ser chamados na thread de eventos do Swing.</p>
 */
public class TelaChat extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel rotuloSessao = new JLabel(" ");
    private final JButton botaoInicio = new Componentes.Botao("Início", Componentes.Estilo.SECUNDARIO);
    private final JButton botaoSair = new Componentes.Botao("Sair do chat", Componentes.Estilo.PERIGO);

    private final JTabbedPane abas = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final Map<Conversa, PainelConversa> paineis = new LinkedHashMap<>();
    private final Map<Conversa, CabecalhoAba> cabecalhos = new HashMap<>();

    private final DefaultListModel<String> modeloOnline = new DefaultListModel<>();
    private final DefaultListModel<String> modeloGrupos = new DefaultListModel<>();
    private final JList<String> listaOnline = new JList<>(modeloOnline);
    private final JList<String> listaGrupos = new JList<>(modeloGrupos);
    private final JLabel rotuloOnline = new JLabel("ONLINE · 0");
    private final JLabel rotuloGrupos = new JLabel("MEUS GRUPOS · 0");
    private final JButton botaoCriarGrupo = new Componentes.Botao("+ Criar", Componentes.Estilo.PRIMARIO);
    private final JButton botaoEntrarGrupo = new Componentes.Botao("Entrar", Componentes.Estilo.SECUNDARIO);
    private final JButton botaoSairGrupo = new Componentes.Botao("Sair", Componentes.Estilo.FANTASMA);

    private final JButton botaoStatus = new Componentes.Botao("Disponível", Componentes.Estilo.SECUNDARIO);
    private final JButton botaoMais = new Componentes.Botao("Mais", Componentes.Estilo.SECUNDARIO);

    private final JLabel rotuloStatus = new JLabel(" ");
    private final Componentes.Campo campoMensagem = new Componentes.Campo("", 0);
    private final JButton botaoEnviar = new Componentes.BotaoEnviar();

    private String meuNome = "";
    private List<String> todosOsGrupos = List.of();

    private BiConsumer<Conversa, String> aoEnviar = (c, t) -> { };
    private Consumer<Conversa> aoAbrirConversa = c -> { };
    private Consumer<String> aoCriarGrupo = n -> { };
    private Consumer<String> aoEntrarNoGrupo = n -> { };
    private Consumer<String> aoSairDoGrupo = n -> { };
    private Consumer<String> aoComando = c -> { };
    private Runnable aoPreferencias = () -> { };
    private Consumer<Conversa> aoDigitar = c -> { };

    private Preferencias preferencias; // pode ser null (sem silêncio nem som configuráveis)
    private Runnable somDeAviso = () -> Toolkit.getDefaultToolkit().beep();
    private boolean avisosLigados = true;
    private Map<String, String> statusPorUsuario = Map.of();
    private String tituloBase;

    public TelaChat() {
        setLayout(new BorderLayout());
        setBackground(Tema.SUPERFICIE);
        Componentes.estilizarAbas(abas);

        garantirAba(Conversa.GERAL);

        add(criarTopo(), BorderLayout.NORTH);
        add(abas, BorderLayout.CENTER);
        add(criarLateral(), BorderLayout.EAST);
        add(criarRodape(), BorderLayout.SOUTH);

        abas.addChangeListener(e -> {
            limparNaoLida(conversaAtiva());
            atualizarAparenciaDasAbas();
            atualizarDica();
            focarEntrada();
        });
        atualizarDica();
        botaoEnviar.addActionListener(e -> disparar());
        campoMensagem.addActionListener(e -> disparar());
        campoMensagem.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                avisarDigitacao();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                // apagar texto não conta como "digitando"
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // mudanças de estilo não se aplicam a campo de texto simples
            }
        });
        botaoStatus.addActionListener(e -> menuDeStatus().show(botaoStatus, 0, botaoStatus.getHeight()));
        botaoMais.addActionListener(e -> menuMais().show(botaoMais, 0, botaoMais.getHeight()));
        botaoStatus.setToolTipText("Como você aparece para os outros");
        botaoMais.setToolTipText("Buscar, exportar, utilidades da UCI e aparência");
    }

    private void avisarDigitacao() {
        String t = campoMensagem.getText();
        if (!t.isBlank() && !t.startsWith("/")) {
            aoDigitar.accept(conversaAtiva());
        }
    }

    // ------------------------------------------------------------------------------------
    // API usada pelo controlador
    // ------------------------------------------------------------------------------------

    /** Chamado ao enviar: recebe a conversa da aba ativa e o texto digitado. */
    public void aoEnviar(BiConsumer<Conversa, String> acao) {
        this.aoEnviar = acao;
    }

    /** Chamado quando o USUÁRIO abre uma conversa nova (permite buscar o histórico dela). */
    public void aoAbrirConversa(Consumer<Conversa> acao) {
        this.aoAbrirConversa = acao;
    }

    public void aoCriarGrupo(Consumer<String> acao) {
        this.aoCriarGrupo = acao;
    }

    public void aoEntrarNoGrupo(Consumer<String> acao) {
        this.aoEntrarNoGrupo = acao;
    }

    public void aoSairDoGrupo(Consumer<String> acao) {
        this.aoSairDoGrupo = acao;
    }

    /** Comando pronto a enviar ao servidor (ex.: "/buscar texto", "/uci resumir"). */
    public void aoComando(Consumer<String> acao) {
        this.aoComando = acao;
    }

    /** Pedido para abrir o diálogo de aparência e avisos. */
    public void aoPreferencias(Runnable acao) {
        this.aoPreferencias = acao;
    }

    /** Chamado (sem limite de frequência) quando o usuário digita na conversa indicada. */
    public void aoDigitar(Consumer<Conversa> acao) {
        this.aoDigitar = acao;
    }

    /** Onde ficam as conversas silenciadas e a opção de som; sem isso, tudo notifica. */
    public void definirPreferencias(Preferencias p) {
        this.preferencias = p;
    }

    /** Troca o som de aviso (os testes contam as chamadas em vez de tocar). */
    void definirSomDeAviso(Runnable som) {
        this.somDeAviso = som;
    }

    /** Liga/desliga som e contagem de não lidas (desligado enquanto a tela é remontada). */
    void definirAvisosLigados(boolean ligados) {
        this.avisosLigados = ligados;
    }

    public void aoSair(Runnable acao) {
        botaoSair.addActionListener(e -> acao.run());
    }

    public void aoIrParaInicio(Runnable acao) {
        botaoInicio.addActionListener(e -> acao.run());
    }

    public void definirSessao(String nome, String destino) {
        this.meuNome = nome;
        rotuloSessao.setText("Conectado como " + nome + "  •  " + destino);
    }

    /** Volta ao estado inicial: só a aba Geral, vazia, e listas limpas. */
    public void limpar() {
        for (Conversa c : new ArrayList<>(paineis.keySet())) {
            if (!c.equals(Conversa.GERAL)) {
                fecharAba(c);
            }
        }
        paineis.get(Conversa.GERAL).limpar();
        limparNaoLida(Conversa.GERAL);
        modeloOnline.clear();
        modeloGrupos.clear();
        todosOsGrupos = List.of();
        rotuloOnline.setText("ONLINE · 0");
        rotuloGrupos.setText("MEUS GRUPOS · 0");
        campoMensagem.setText("");
        mostrarStatus(" ");
    }

    public void limparEntrada() {
        campoMensagem.setText("");
    }

    public void focarEntrada() {
        campoMensagem.requestFocusInWindow();
    }

    /** Texto de apoio abaixo das abas (ex.: "UCI está digitando…"). */
    public void mostrarStatus(String texto) {
        rotuloStatus.setText(texto == null || texto.isBlank() ? " " : texto);
    }

    /** Status de quem não está disponível (usuário em minúsculas -> rótulo). */
    public void atualizarStatus(Map<String, String> porUsuario) {
        this.statusPorUsuario = Map.copyOf(porUsuario);
        listaOnline.repaint();
        String meu = statusPorUsuario.getOrDefault(Usuario.chave(meuNome), "disponivel");
        botaoStatus.setText(rotuloDoStatus(meu));
    }

    String statusDe(String nome) {
        return statusPorUsuario.getOrDefault(Usuario.chave(nome), "disponivel");
    }

    private static String rotuloDoStatus(String status) {
        return switch (status) {
            case "ocupado" -> "Ocupado";
            case "ausente" -> "Ausente";
            default -> "Disponível";
        };
    }

    public void atualizarOnline(List<String> nomes) {
        modeloOnline.clear();
        nomes.forEach(modeloOnline::addElement);
        rotuloOnline.setText("ONLINE · " + nomes.size());
    }

    /**
     * Atualiza as listas de grupos. Abas de grupos de que o usuário já não faz parte são fechadas.
     *
     * @param meus grupos de que sou membro
     * @param todos todos os grupos existentes
     */
    public void atualizarGrupos(List<String> meus, List<String> todos) {
        todosOsGrupos = List.copyOf(todos);
        modeloGrupos.clear();
        meus.forEach(modeloGrupos::addElement);
        rotuloGrupos.setText("MEUS GRUPOS · " + meus.size());
        for (Conversa c : new ArrayList<>(paineis.keySet())) {
            if (c.tipo() == Conversa.Tipo.GRUPO && !meus.contains(c.chave())) {
                fecharAba(c);
            }
        }
    }

    /**
     * Exibe uma mensagem na conversa (criando a aba se preciso, sem trocar a aba selecionada).
     * Se a aba não está selecionada, ela é marcada como não lida.
     */
    public void adicionarMensagem(Conversa conversa, String hora, String remetente, String conteudo) {
        PainelConversa painel = garantirAba(conversa);
        boolean minha = remetente.equalsIgnoreCase(meuNome);
        painel.adicionarMensagem(hora, remetente, conteudo, minha);
        if (!minha) {
            notificar(conversa, painel);
        }
    }

    public void adicionarAviso(Conversa conversa, String aviso) {
        garantirAba(conversa).adicionarAviso(aviso);
    }

    /**
     * Mensagem de outra pessoa: conta como não lida se a aba não está à vista e toca o som se a aba
     * está escondida ou a janela está em segundo plano. Conversas silenciadas ficam quietas.
     */
    private void notificar(Conversa conversa, PainelConversa painel) {
        if (!avisosLigados || (preferencias != null && preferencias.estaSilenciada(conversa))) {
            return;
        }
        boolean abaOculta = abas.getSelectedComponent() != painel;
        if (abaOculta) {
            cabecalhos.get(conversa).somarNaoLida();
        }
        if ((abaOculta || !janelaAtiva()) && (preferencias == null || preferencias.som())) {
            somDeAviso.run();
        }
        atualizarTituloDaJanela();
    }

    private boolean janelaAtiva() {
        Window w = SwingUtilities.getWindowAncestor(this);
        return w == null || w.isActive();
    }

    /** Total de mensagens não lidas em todas as abas. */
    int totalNaoLidas() {
        return cabecalhos.values().stream().mapToInt(CabecalhoAba::naoLidas).sum();
    }

    private void atualizarTituloDaJanela() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof java.awt.Frame f) {
            if (tituloBase == null) {
                tituloBase = f.getTitle().replaceFirst("^\\(\\d+\\) ", "");
            }
            int total = totalNaoLidas();
            f.setTitle((total > 0 ? "(" + total + ") " : "") + tituloBase);
        }
    }

    public void adicionarCabecalho(Conversa conversa, String cabecalho) {
        garantirAba(conversa).adicionarCabecalho(cabecalho);
    }

    /** Erro de uma ação do usuário: aparece na aba que ele está vendo. */
    public void adicionarErro(String erro) {
        paineis.get(conversaAtiva()).adicionarErro(erro);
    }

    /** Resposta de um comando (ex.: /ajuda): aparece na aba ativa. */
    public void adicionarTexto(String linha) {
        paineis.get(conversaAtiva()).adicionarTexto(linha);
    }

    /** Abre (e seleciona) a conversa. @return {@code true} se a aba foi criada agora */
    public boolean abrirConversa(Conversa conversa) {
        boolean nova = !paineis.containsKey(conversa);
        PainelConversa painel = garantirAba(conversa);
        abas.setSelectedComponent(painel);
        limparNaoLida(conversa);
        focarEntrada();
        if (nova) {
            aoAbrirConversa.accept(conversa);
        }
        return nova;
    }

    /** Recria as abas abertas (mesmo vazias) e seleciona a ativa, sem pedir histórico de novo. */
    void restaurarAbas(List<Conversa> abertas, Conversa ativa) {
        for (Conversa c : abertas) {
            garantirAba(c);
        }
        if (paineis.containsKey(ativa)) {
            abas.setSelectedComponent(paineis.get(ativa));
            limparNaoLida(ativa);
        }
    }

    /** Alterna o silêncio da conversa ativa. @return {@code true} se ficou silenciada */
    boolean alternarSilencioDaAtiva() {
        if (preferencias == null) {
            return false;
        }
        Conversa ativa = conversaAtiva();
        boolean silenciada = preferencias.alternarSilencio(ativa);
        CabecalhoAba h = cabecalhos.get(ativa);
        if (h != null) {
            h.definirMudo(silenciada);
            if (silenciada) {
                h.definirNaoLida(false);
            }
        }
        atualizarTituloDaJanela();
        return silenciada;
    }

    /** Salva a conversa ativa em texto. @return o arquivo gravado */
    Path exportarConversaAtiva(Path arquivo) throws java.io.IOException {
        Conversa ativa = conversaAtiva();
        return Exportacao.salvar(arquivo, ativa.titulo(), textoDa(ativa), java.time.LocalDateTime.now());
    }

    public Conversa conversaAtiva() {
        Component selecionado = abas.getSelectedComponent();
        for (Map.Entry<Conversa, PainelConversa> e : paineis.entrySet()) {
            if (e.getValue() == selecionado) {
                return e.getKey();
            }
        }
        return Conversa.GERAL;
    }

    // ------------------------------------------------------------------------------------
    // Acesso para testes no mesmo pacote
    // ------------------------------------------------------------------------------------

    JButton botaoSair() {
        return botaoSair;
    }

    JButton botaoInicio() {
        return botaoInicio;
    }

    JButton botaoEnviar() {
        return botaoEnviar;
    }

    JTextField campoMensagem() {
        return campoMensagem;
    }

    String textoDa(Conversa conversa) {
        PainelConversa p = paineis.get(conversa);
        return p == null ? "" : p.conteudo();
    }

    String textoMensagens() {
        return textoDa(Conversa.GERAL);
    }

    List<String> online() {
        return Collections.list(modeloOnline.elements());
    }

    List<String> meusGrupos() {
        return Collections.list(modeloGrupos.elements());
    }

    List<String> todosOsGrupos() {
        return todosOsGrupos;
    }

    List<Conversa> conversasAbertas() {
        return new ArrayList<>(paineis.keySet());
    }

    boolean temNaoLida(Conversa conversa) {
        CabecalhoAba h = cabecalhos.get(conversa);
        return h != null && h.naoLida();
    }

    String textoDoStatus() {
        return rotuloStatus.getText();
    }

    void fecharAba(Conversa conversa) {
        if (conversa.equals(Conversa.GERAL)) {
            return;
        }
        PainelConversa painel = paineis.remove(conversa);
        cabecalhos.remove(conversa);
        if (painel != null) {
            abas.remove(painel);
        }
    }

    // ------------------------------------------------------------------------------------
    // Construção da tela
    // ------------------------------------------------------------------------------------

    private JPanel criarTopo() {
        JPanel topo = new JPanel(new BorderLayout(14, 0));
        topo.setBackground(Tema.SUPERFICIE);
        topo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDA),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));

        JLabel titulo = new JLabel("Chat Multiusuário");
        titulo.setFont(Tema.fonte(Font.BOLD, 16f));
        titulo.setForeground(Tema.TEXTO);
        rotuloSessao.setFont(Tema.fonte(Font.PLAIN, 12.5f));
        rotuloSessao.setForeground(Tema.TEXTO_SUAVE);
        rotuloSessao.setIcon(new PontoIcone(Tema.SUCESSO, 9));
        rotuloSessao.setIconTextGap(7);

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 1));
        textos.setOpaque(false);
        textos.add(titulo);
        textos.add(rotuloSessao);

        JPanel identidade = new JPanel(new BorderLayout(12, 0));
        identidade.setOpaque(false);
        identidade.add(new Componentes.Logo(40), BorderLayout.WEST);
        identidade.add(textos, BorderLayout.CENTER);
        topo.add(identidade, BorderLayout.CENTER);

        botaoInicio.setToolTipText("Volta à tela inicial sem sair da conversa");
        botaoSair.setToolTipText("Desconecta do servidor e volta à tela inicial");

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botoes.setOpaque(false);
        botoes.add(botaoStatus);
        botoes.add(botaoMais);
        botoes.add(botaoInicio);
        botoes.add(botaoSair);
        JPanel direita = new JPanel(new GridBagLayout());
        direita.setOpaque(false);
        direita.add(botoes);
        topo.add(direita, BorderLayout.EAST);
        return topo;
    }

    // ------------------------------------------------------------------------------------
    // Menus
    // ------------------------------------------------------------------------------------

    private JPopupMenu menuDeStatus() {
        JPopupMenu menu = new JPopupMenu();
        for (String[] opcao : new String[][]{{"Disponível", "disponivel"}, {"Ocupado", "ocupado"}, {"Ausente", "ausente"}}) {
            JMenuItem item = new JMenuItem(opcao[0]);
            item.addActionListener(e -> aoComando.accept("/status " + opcao[1]));
            menu.add(item);
        }
        return menu;
    }

    private JPopupMenu menuMais() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Buscar nas conversas…", this::pedirBusca));
        menu.add(item("Exportar esta conversa…", this::pedirExportacao));
        menu.addSeparator();
        menu.add(item("UCI: resumir esta conversa", () -> aoComando.accept(comandoDeResumo(conversaAtiva()))));
        menu.add(item("UCI: traduzir um texto…", this::pedirTraducao));
        menu.add(item("UCI: explicar um assunto…", this::pedirExplicacao));
        JMenu modos = new JMenu("UCI: jeito de responder");
        for (String[] modo : new String[][]{{"Padrão", "padrao"}, {"Formal", "formal"},
                {"Descontraída", "descontraida"}, {"Professora", "professora"}}) {
            modos.add(item(modo[0], () -> aoComando.accept("/uci modo " + modo[1])));
        }
        menu.add(modos);
        menu.addSeparator();
        boolean silenciada = preferencias != null && preferencias.estaSilenciada(conversaAtiva());
        menu.add(item(silenciada ? "Reativar avisos desta conversa" : "Silenciar esta conversa",
                this::alternarSilencioDaAtiva));
        menu.add(item("Aparência e avisos…", () -> aoPreferencias.run()));
        return menu;
    }

    private static JMenuItem item(String texto, Runnable acao) {
        JMenuItem item = new JMenuItem(texto);
        item.addActionListener(e -> acao.run());
        return item;
    }

    /** Comando que pede à UCI o resumo da conversa indicada. */
    static String comandoDeResumo(Conversa c) {
        return switch (c.tipo()) {
            case GERAL -> "/uci resumir";
            case GRUPO -> "/uci resumir #" + c.chave();
            case PRIVADA -> Usuario.reservado(c.titulo()) ? "/uci resumir" : "/uci resumir " + c.titulo();
        };
    }

    private void pedirBusca() {
        String termo = JOptionPane.showInputDialog(this, "Buscar (mínimo de 2 caracteres):",
                "Buscar nas conversas", JOptionPane.PLAIN_MESSAGE);
        if (termo != null && !termo.isBlank()) {
            aoComando.accept("/buscar " + termo.trim());
        }
    }

    private void pedirTraducao() {
        String idioma = JOptionPane.showInputDialog(this, "Traduzir para qual idioma?", "Traduzir com a UCI",
                JOptionPane.PLAIN_MESSAGE);
        if (idioma == null || idioma.isBlank()) {
            return;
        }
        String texto = JOptionPane.showInputDialog(this, "Texto a traduzir:", "Traduzir com a UCI",
                JOptionPane.PLAIN_MESSAGE);
        if (texto != null && !texto.isBlank()) {
            aoComando.accept("/uci traduzir " + idioma.trim().split("\\s+")[0] + " " + texto.trim());
        }
    }

    private void pedirExplicacao() {
        String texto = JOptionPane.showInputDialog(this, "O que a UCI deve explicar?", "Explicar com a UCI",
                JOptionPane.PLAIN_MESSAGE);
        if (texto != null && !texto.isBlank()) {
            aoComando.accept("/uci explicar " + texto.trim());
        }
    }

    /** Abre a escolha do arquivo e exporta a conversa ativa. */
    void exportar() {
        pedirExportacao();
    }

    private void pedirExportacao() {
        javax.swing.JFileChooser escolha = new javax.swing.JFileChooser();
        escolha.setDialogTitle("Exportar conversa");
        escolha.setSelectedFile(new java.io.File(Exportacao.nomeSugerido(conversaAtiva().titulo(),
                java.time.LocalDateTime.now())));
        if (escolha.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Path gravado = exportarConversaAtiva(escolha.getSelectedFile().toPath());
            adicionarTexto("Conversa exportada para " + gravado.toAbsolutePath());
        } catch (java.io.IOException e) {
            adicionarErro("Não foi possível exportar: " + e.getMessage());
        }
    }

    private void mostrarMenuDoGrupo(MouseEvent e, JList<String> lista) {
        int indice = lista.locationToIndex(e.getPoint());
        if (indice < 0) {
            return;
        }
        lista.setSelectedIndex(indice);
        String grupo = lista.getModel().getElementAt(indice);
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Abrir #" + grupo, () -> abrirConversa(Conversa.grupo(grupo))));
        menu.add(item("Ver membros", () -> {
            abrirConversa(Conversa.grupo(grupo));
            aoComando.accept("/grupo membros " + grupo);
        }));
        menu.add(item("Remover um membro… (só o criador)", () -> {
            String alvo = JOptionPane.showInputDialog(this, "Nome do usuário a remover de #" + grupo + ":",
                    "Remover membro", JOptionPane.PLAIN_MESSAGE);
            if (alvo != null && !alvo.isBlank()) {
                aoComando.accept("/grupo remover " + grupo + " " + alvo.trim());
            }
        }));
        menu.show(lista, e.getX(), e.getY());
    }

    private JPanel criarLateral() {
        JPanel lateral = new JPanel(new GridLayout(2, 1));
        lateral.setPreferredSize(new Dimension(250, 0));
        lateral.setBackground(Tema.SUPERFICIE);
        lateral.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Tema.BORDA));

        configurarLista(listaOnline, true);
        configurarLista(listaGrupos, false);
        listaOnline.setToolTipText("Clique duas vezes em um usuário para conversar em privado");
        listaGrupos.setToolTipText("Clique duas vezes em um grupo para abrir a conversa");

        lateral.add(criarSecao(rotuloOnline, listaOnline, null));

        JPanel botoes = new JPanel(new GridLayout(1, 3, 6, 0));
        botoes.setOpaque(false);
        botoes.setBorder(BorderFactory.createEmptyBorder(8, 12, 14, 12));
        for (JButton b : List.of(botaoCriarGrupo, botaoEntrarGrupo, botaoSairGrupo)) {
            b.setBorder(BorderFactory.createEmptyBorder(7, 6, 7, 6));
            b.setFont(Tema.fonte(Font.BOLD, 12f));
            botoes.add(b);
        }
        botaoCriarGrupo.setToolTipText("Cria um novo grupo");
        botaoEntrarGrupo.setToolTipText("Entra em um grupo existente");
        botaoSairGrupo.setToolTipText("Sai do grupo selecionado");
        lateral.add(criarSecao(rotuloGrupos, listaGrupos, botoes));

        botaoCriarGrupo.addActionListener(e -> pedirNovoGrupo());
        botaoEntrarGrupo.addActionListener(e -> pedirEntradaEmGrupo());
        botaoSairGrupo.addActionListener(e -> pedirSaidaDeGrupo());
        return lateral;
    }

    private JPanel criarSecao(JLabel titulo, JList<String> lista, JPanel rodape) {
        JPanel secao = new JPanel(new BorderLayout());
        secao.setOpaque(false);
        titulo.setFont(Tema.fonte(Font.BOLD, 11f));
        titulo.setForeground(Tema.TEXTO_SUAVE);
        titulo.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 18));
        secao.add(titulo, BorderLayout.NORTH);
        JScrollPane rolagem = new JScrollPane(lista);
        rolagem.getViewport().setBackground(Tema.SUPERFICIE);
        Componentes.estilizarRolagem(rolagem);
        secao.add(rolagem, BorderLayout.CENTER);
        if (rodape != null) {
            secao.add(rodape, BorderLayout.SOUTH);
        }
        return secao;
    }

    private void configurarLista(JList<String> lista, boolean usuarios) {
        lista.setBackground(Tema.SUPERFICIE);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setFixedCellHeight(48);
        lista.setCellRenderer(usuarios ? new LinhaUsuario() : new LinhaGrupo());
        lista.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (!usuarios) {
            lista.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        mostrarMenuDoGrupo(e, lista);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        mostrarMenuDoGrupo(e, lista);
                    }
                }
            });
        }
        lista.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                int indice = lista.locationToIndex(e.getPoint());
                if (indice < 0) {
                    return;
                }
                String valor = lista.getModel().getElementAt(indice);
                if (usuarios) {
                    abrirPrivado(valor);
                } else {
                    abrirConversa(Conversa.grupo(valor));
                }
            }
        });
    }

    private JPanel criarRodape() {
        JPanel rodape = new JPanel(new BorderLayout(10, 4));
        rodape.setBackground(Tema.SUPERFICIE);
        rodape.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.BORDA),
                BorderFactory.createEmptyBorder(8, 16, 14, 16)));

        rotuloStatus.setFont(Tema.fonte(Font.ITALIC, 12f));
        rotuloStatus.setForeground(Tema.UCI);
        rotuloStatus.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 0));
        rodape.add(rotuloStatus, BorderLayout.NORTH);

        campoMensagem.setToolTipText("Digite e pressione Enter. Escreva @UCI para chamar a assistente. Comandos: /ajuda");
        rodape.add(campoMensagem, BorderLayout.CENTER);
        rodape.add(botaoEnviar, BorderLayout.EAST);
        return rodape;
    }

    /** O texto de dica do campo muda conforme a conversa aberta. */
    private void atualizarDica() {
        Conversa c = conversaAtiva();
        String dica = switch (c.tipo()) {
            case GERAL -> "Mensagem para o chat geral…  (use @UCI para chamar a assistente)";
            case GRUPO -> "Mensagem para #" + c.chave() + "…  (use @UCI para chamar a assistente)";
            case PRIVADA -> Usuario.reservado(c.titulo())
                    ? "Pergunte alguma coisa à UCI…"
                    : "Mensagem privada para " + c.titulo() + "…";
        };
        campoMensagem.definirDica(dica);
    }

    private void atualizarAparenciaDasAbas() {
        Conversa ativa = conversaAtiva();
        cabecalhos.forEach((conversa, h) -> h.definirSelecionada(conversa.equals(ativa)));
    }

    // ------------------------------------------------------------------------------------
    // Linhas das listas laterais (avatar + nome + legenda)
    // ------------------------------------------------------------------------------------

    private final class LinhaUsuario extends JComponent implements ListCellRenderer<String> {
        private static final long serialVersionUID = 1L;

        private String nome = "";
        private boolean selecionado;

        @Override
        public Component getListCellRendererComponent(JList<? extends String> l, String valor, int i,
                                                      boolean sel, boolean foco) {
            this.nome = String.valueOf(valor);
            this.selecionado = sel;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 48);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            if (selecionado) {
                g2.setColor(Tema.PRIMARIA_CLARA);
                g2.fill(new RoundRectangle2D.Float(8, 2, getWidth() - 16, getHeight() - 4, 14, 14));
            }
            int d = 34;
            int y = (getHeight() - d) / 2;
            Tema.avatar(g2, nome, 16, y, d);
            g2.setColor(Tema.SUPERFICIE);
            g2.fillOval(16 + d - 12, y + d - 12, 13, 13);
            String status = Usuario.reservado(nome) ? "disponivel" : statusDe(nome);
            g2.setColor(switch (status) {
                case "ocupado" -> Tema.ERRO;
                case "ausente" -> new Color(0xF59E0B);
                default -> Tema.SUCESSO;
            });
            g2.fillOval(16 + d - 10, y + d - 10, 9, 9);

            boolean eu = nome.equalsIgnoreCase(meuNome);
            boolean uci = Usuario.reservado(nome);
            String situacao = "disponivel".equals(status) ? "online" : status;
            String legenda = eu ? "você · " + situacao : uci ? "assistente de IA" : situacao;
            Font fNome = Tema.fonte(eu || uci ? Font.BOLD : Font.PLAIN, 13.5f);
            Font fLegenda = Tema.fonte(Font.PLAIN, 11.5f);
            int x = 16 + d + 12;
            g2.setFont(fNome);
            g2.setColor(Tema.TEXTO);
            g2.drawString(nome, x, getHeight() / 2f - 2);
            g2.setFont(fLegenda);
            g2.setColor(uci ? Tema.UCI : Tema.TEXTO_SUAVE);
            g2.drawString(legenda, x, getHeight() / 2f + 13);
            g2.dispose();
        }
    }

    private final class LinhaGrupo extends JComponent implements ListCellRenderer<String> {
        private static final long serialVersionUID = 1L;

        private String nome = "";
        private boolean selecionado;

        @Override
        public Component getListCellRendererComponent(JList<? extends String> l, String valor, int i,
                                                      boolean sel, boolean foco) {
            this.nome = String.valueOf(valor);
            this.selecionado = sel;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 48);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Tema.suavizar(g);
            if (selecionado) {
                g2.setColor(Tema.PRIMARIA_CLARA);
                g2.fill(new RoundRectangle2D.Float(8, 2, getWidth() - 16, getHeight() - 4, 14, 14));
            }
            int d = 34;
            int y = (getHeight() - d) / 2;
            Color cor = Tema.corDaPessoa(nome);
            g2.setColor(Tema.misturar(cor, Tema.SUPERFICIE, 0.82f));
            g2.fill(new RoundRectangle2D.Float(16, y, d, d, 12, 12));
            g2.setFont(Tema.fonte(Font.BOLD, 17f));
            g2.setColor(cor);
            g2.drawString("#", 16 + (d - Tema.larguraTexto(g2.getFont(), "#")) / 2f, y + d / 2f + 6);
            g2.setFont(Tema.fonte(Font.PLAIN, 14f));
            g2.setColor(Tema.TEXTO);
            g2.drawString(nome, 16 + d + 12, getHeight() / 2f + 5);
            g2.dispose();
        }
    }

    /** Bolinha colorida usada como ícone de status. */
    private static final class PontoIcone implements Icon {
        private final Color cor;
        private final int tamanho;

        PontoIcone(Color cor, int tamanho) {
            this.cor = cor;
            this.tamanho = tamanho;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Tema.suavizar(g);
            g2.setColor(cor);
            g2.fillOval(x, y, tamanho, tamanho);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return tamanho;
        }

        @Override
        public int getIconHeight() {
            return tamanho;
        }
    }

    // ------------------------------------------------------------------------------------
    // Abas
    // ------------------------------------------------------------------------------------

    private PainelConversa garantirAba(Conversa conversa) {
        PainelConversa existente = paineis.get(conversa);
        if (existente != null) {
            return existente;
        }
        PainelConversa painel = new PainelConversa();
        CabecalhoAba cabecalho = new CabecalhoAba(conversa);
        paineis.put(conversa, painel);
        cabecalhos.put(conversa, cabecalho);
        abas.addTab(conversa.titulo(), painel);
        abas.setTabComponentAt(abas.indexOfComponent(painel), cabecalho);
        return painel;
    }

    private void limparNaoLida(Conversa conversa) {
        CabecalhoAba h = cabecalhos.get(conversa);
        if (h != null) {
            h.definirNaoLida(false);
        }
        atualizarTituloDaJanela();
    }

    private void abrirPrivado(String nome) {
        if (nome.equalsIgnoreCase(meuNome)) {
            return; // conversar consigo mesmo não faz sentido
        }
        abrirConversa(Conversa.privada(nome));
    }

    // ------------------------------------------------------------------------------------
    // Ações dos botões de grupo (diálogos)
    // ------------------------------------------------------------------------------------

    private void pedirNovoGrupo() {
        String nome = JOptionPane.showInputDialog(this,
                "Nome do novo grupo (2 a 20 caracteres, sem espaços):", "Criar grupo", JOptionPane.PLAIN_MESSAGE);
        if (nome != null && !nome.isBlank()) {
            aoCriarGrupo.accept(nome.trim());
        }
    }

    private void pedirEntradaEmGrupo() {
        List<String> meus = meusGrupos();
        List<String> disponiveis = todosOsGrupos.stream().filter(g -> !meus.contains(g)).toList();
        if (disponiveis.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Não há outros grupos para entrar. Que tal criar um?",
                    "Entrar em grupo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object escolha = JOptionPane.showInputDialog(this, "Escolha o grupo:", "Entrar em grupo",
                JOptionPane.QUESTION_MESSAGE, null, disponiveis.toArray(), disponiveis.get(0));
        if (escolha != null) {
            aoEntrarNoGrupo.accept(escolha.toString());
        }
    }

    private void pedirSaidaDeGrupo() {
        String grupo = listaGrupos.getSelectedValue();
        if (grupo == null && conversaAtiva().tipo() == Conversa.Tipo.GRUPO) {
            grupo = conversaAtiva().chave();
        }
        if (grupo == null) {
            JOptionPane.showMessageDialog(this, "Selecione um grupo na lista (ou abra a aba dele) para sair.",
                    "Sair do grupo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int resposta = JOptionPane.showConfirmDialog(this, "Sair do grupo #" + grupo + "?",
                "Sair do grupo", JOptionPane.YES_NO_OPTION);
        if (resposta == JOptionPane.YES_OPTION) {
            aoSairDoGrupo.accept(grupo);
        }
    }

    private void disparar() {
        String texto = campoMensagem.getText();
        if (!texto.isBlank()) {
            aoEnviar.accept(conversaAtiva(), texto);
        }
    }

    // ------------------------------------------------------------------------------------
    // Cabeçalho (título + botão de fechar) de cada aba
    // ------------------------------------------------------------------------------------

    private final class CabecalhoAba extends JPanel {
        private static final long serialVersionUID = 1L;

        private final JLabel rotulo;
        private final Conversa conversa;
        private final JComponent ponto = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Tema.suavizar(g);
                g2.setColor(Tema.PRIMARIA);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
                g2.setColor(Color.WHITE);
                Font f = Tema.fonte(Font.BOLD, 10f);
                g2.setFont(f);
                String t = textoDoContador();
                g2.drawString(t, (getWidth() - Tema.larguraTexto(f, t)) / 2f,
                        (getHeight() + Tema.subida(f) * 0.7f) / 2f + 1);
                g2.dispose();
            }
        };
        private int naoLidas;
        private boolean mudo;
        private boolean selecionada;

        private String textoDoContador() {
            return naoLidas > 9 ? "9+" : String.valueOf(naoLidas);
        }

        CabecalhoAba(Conversa conversa) {
            super(new FlowLayout(FlowLayout.LEFT, 5, 2));
            setOpaque(false);
            this.conversa = conversa;
            this.rotulo = new JLabel(conversa.titulo() + (preferencias != null && preferencias.estaSilenciada(conversa) ? " (mudo)" : ""));
            rotulo.setFont(Tema.fonte(Font.PLAIN, 13f));
            ponto.setPreferredSize(new Dimension(Math.round(18 * Tema.escala()), Math.round(16 * Tema.escala())));
            ponto.setVisible(false);
            JPopupMenu menuAba = new JPopupMenu();
            menuAba.add(item("Silenciar / reativar avisos", () -> {
                abas.setSelectedComponent(paineis.get(conversa));
                alternarSilencioDaAtiva();
            }));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        menuAba.show(CabecalhoAba.this, e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        menuAba.show(CabecalhoAba.this, e.getX(), e.getY());
                    }
                }
            });
            add(ponto);
            add(rotulo);
            if (!conversa.equals(Conversa.GERAL)) {
                JButton fechar = new Componentes.Fechar();
                fechar.setToolTipText("Fechar esta aba (a conversa continua; abra de novo para ver o histórico)");
                fechar.addActionListener(e -> fecharAba(conversa));
                add(fechar);
            }
            aplicarCor();
        }

        boolean naoLida() {
            return naoLidas > 0;
        }

        int naoLidas() {
            return naoLidas;
        }

        void somarNaoLida() {
            naoLidas++;
            ponto.setVisible(true);
            ponto.repaint();
            aplicarCor();
        }

        void definirNaoLida(boolean valor) {
            if (!valor) {
                naoLidas = 0;
            } else if (naoLidas == 0) {
                naoLidas = 1;
            }
            ponto.setVisible(naoLidas > 0);
            ponto.repaint();
            aplicarCor();
        }

        void definirMudo(boolean valor) {
            this.mudo = valor;
            rotulo.setText(conversa.titulo() + (valor ? " (mudo)" : ""));
            aplicarCor();
        }

        void definirSelecionada(boolean valor) {
            this.selecionada = valor;
            aplicarCor();
        }

        private void aplicarCor() {
            Color cor = selecionada ? Tema.PRIMARIA_TEXTO
                    : naoLida() ? Tema.TEXTO
                    : conversa.tipo() == Conversa.Tipo.PRIVADA && Usuario.reservado(conversa.titulo()) ? Tema.UCI
                    : Tema.TEXTO_SUAVE;
            rotulo.setForeground(cor);
            rotulo.setFont(Tema.fonte(Font.PLAIN, 13f)); // mesmo peso sempre: a aba não muda de largura ao selecionar
        }
    }
}
