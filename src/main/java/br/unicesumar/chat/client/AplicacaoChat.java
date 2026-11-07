package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocolo.Evento;
import br.unicesumar.chat.protocolo.Interpretador;
import br.unicesumar.chat.protocolo.Protocolo;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.CardLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/**
 * Controlador da interface gráfica: alterna entre {@link TelaInicio} e {@link TelaChat},
 * conecta em segundo plano (a janela nunca congela) e roteia cada linha vinda da rede
 * ({@link Interpretador}) para a aba certa: geral, grupo ou conversa privada.
 *
 * <p>Fluxo: <b>Início</b> → "Entrar no chat" → <b>Chat</b>. No chat, "Início" volta à
 * tela inicial <i>sem</i> desconectar (e lá aparece "Voltar ao chat"); "Sair do chat"
 * desconecta de fato.</p>
 *
 * <p>Ao conectar, o cliente envia {@code /modo gui}; a partir daí o servidor avisa sozinho
 * quando a lista de online ou de grupos muda (não há consultas repetidas).</p>
 *
 * <p>Threads: a rede chama {@link #aoReceber}/{@link #aoDesconectar} em sua própria thread;
 * tudo que mexe em componentes Swing é reenviado à thread de eventos (EDT).</p>
 */
public class AplicacaoChat extends JPanel implements ConexaoChat.Ouvinte {

    private static final long serialVersionUID = 1L;

    private static final String CARTAO_INICIO = "inicio";
    private static final String CARTAO_CHAT = "chat";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern MENCAO_UCI =
            Pattern.compile("(?<![\\p{L}\\p{N}_])@uci(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int ESPERA_MAXIMA_UCI_MS = 45_000;

    private final transient CardLayout cartoes = new CardLayout();
    private TelaInicio inicio;
    private TelaChat chat;
    private final transient Preferences preferencias;
    private final transient Preferencias prefs;

    /** Tudo o que apareceu no chat, para remontar a janela (trocar tema/fonte) sem perder a conversa. */
    private final transient List<Object> transcricao = new ArrayList<>();
    private static final int MAX_TRANSCRICAO = 3000;
    private boolean reproduzindo;
    private String cartaoAtual = CARTAO_INICIO;
    private String destinoAtual = "";

    private record Eco(Conversa conversa, String hora, String remetente, String texto) { }

    private record AvisoLocal(Conversa conversa, String texto) { }

    // "digitando": envio limitado a um aviso a cada 3 s por conversa; exibição some em 4 s.
    private static final long INTERVALO_DIGITANDO_NS = 3_000_000_000L;
    private static final int EXIBIR_DIGITANDO_MS = 4_000;
    transient java.util.function.LongSupplier relogioNs = System::nanoTime;
    private long ultimoAvisoDigitando;
    private Conversa ultimaConversaDigitando;
    private transient Timer temporizadorDigitando;
    private String quemDigita;
    /** Jeito da UCI que o servidor está usando para este usuário (começa "padrao" a cada conexão). */
    private String modoUciNoServidor = "padrao";

    // Estado: só é lido/alterado na EDT.
    private transient ConexaoChat conexao;
    private String nomeAtual = "";
    private transient Timer temporizadorUci;

    public AplicacaoChat() {
        this(Preferences.userNodeForPackage(AplicacaoChat.class));
    }

    /** @param preferencias onde lembrar servidor, porta e nome (os testes passam um nó isolado) */
    AplicacaoChat(Preferences preferencias) {
        this.preferencias = preferencias;
        this.prefs = new Preferencias(preferencias);
        setLayout(cartoes);
        prefs.aplicarAoTema("");
        montarTelas();
        inicio.definirDados(
                preferencias.get("servidor", "localhost"),
                preferencias.get("porta", "5000"),
                preferencias.get("nome", ""));
        mostrar(CARTAO_INICIO);
    }

    /** Cria as duas telas (com a aparência atual do {@link Tema}) e liga seus botões ao controlador. */
    private void montarTelas() {
        if (inicio != null) {
            remove(inicio);
            remove(chat);
        }
        inicio = new TelaInicio();
        chat = new TelaChat();
        chat.definirPreferencias(prefs);
        add(inicio, CARTAO_INICIO);
        add(chat, CARTAO_CHAT);

        inicio.aoEntrar(this::entrar);
        inicio.aoVoltar(() -> mostrar(CARTAO_CHAT));
        chat.aoEnviar(this::enviar);
        chat.aoAbrirConversa(this::pedirHistorico);
        chat.aoCriarGrupo(nome -> comando("/grupo criar " + nome));
        chat.aoEntrarNoGrupo(nome -> comando("/grupo entrar " + nome));
        chat.aoSairDoGrupo(nome -> comando("/grupo sair " + nome));
        chat.aoSair(this::sair);
        chat.aoIrParaInicio(() -> mostrar(CARTAO_INICIO));
        chat.aoComando(this::comando);
        chat.aoPreferencias(this::abrirPreferencias);
        chat.aoDigitar(this::aoDigitar);
    }

    private void abrirPreferencias() {
        DialogoPreferencias.abrir(this, prefs, this::reaplicarAparencia);
    }

    /**
     * Aplica as preferências (tema, fonte, cor, compacto) remontando as telas e reproduzindo a
     * conversa guardada. Nada se perde: mensagens, abas abertas e aba ativa voltam como estavam.
     */
    void reaplicarAparencia() {
        List<Conversa> abertas = chat.conversasAbertas();
        Conversa ativa = chat.conversaAtiva();
        String servidor = inicio.servidor();
        String porta = inicio.porta();
        String nome = inicio.nome();
        String modoUci = prefs.modoUci();

        prefs.aplicarAoTema(nomeAtual);
        montarTelas();
        inicio.definirDados(servidor, porta, nome);

        if (conexao != null) {
            chat.definirSessao(nomeAtual, destinoAtual);
            chat.definirAvisosLigados(false);
            reproduzindo = true;
            try {
                for (Object entrada : List.copyOf(transcricao)) {
                    if (entrada instanceof String linha) {
                        processarLinha(linha);
                    } else if (entrada instanceof Eco eco) {
                        chat.adicionarMensagem(eco.conversa(), eco.hora(), eco.remetente(), eco.texto());
                    } else if (entrada instanceof AvisoLocal aviso) {
                        chat.adicionarAviso(aviso.conversa(), aviso.texto());
                    }
                }
            } finally {
                reproduzindo = false;
                chat.definirAvisosLigados(true);
            }
            chat.restaurarAbas(abertas, ativa);
            inicio.mostrarConversaEmAndamento(nomeAtual);
            if (!modoUci.equals(modoUciNoServidor)) { // só reenvia se o usuário trocou o jeito da UCI
                comando("/uci modo " + modoUci);
            }
        }
        mostrar(cartaoAtual);
        revalidate();
        repaint();
    }

    private void guardar(Object entrada) {
        if (reproduzindo) {
            return;
        }
        transcricao.add(entrada);
        if (transcricao.size() > MAX_TRANSCRICAO) {
            transcricao.subList(0, transcricao.size() - MAX_TRANSCRICAO).clear();
        }
    }

    /** Chamado ao fechar a janela: desconecta (se estiver conectado). */
    public void encerrar() {
        if (conexao != null) {
            conexao.desconectar();
            conexao = null;
        }
    }

    // ------------------------------------------------------------------------------------
    // Ações da tela
    // ------------------------------------------------------------------------------------

    private void entrar() {
        String servidor = inicio.servidor().trim();
        String nome = inicio.nome().trim();
        int porta;
        try {
            porta = Integer.parseInt(inicio.porta().trim());
            if (porta < 1 || porta > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            inicio.mostrarErro("Porta inválida: use um número entre 1 e 65535.");
            return;
        }
        if (servidor.isEmpty()) {
            inicio.mostrarErro("Informe o endereço do servidor.");
            return;
        }
        try {
            nome = new Usuario(nome).getNome(); // mesma regra do servidor
        } catch (IllegalArgumentException e) {
            inicio.mostrarErro(e.getMessage());
            inicio.focarNome();
            return;
        }

        if (conexao != null) { // já conectado: um novo "Entrar" substitui a conversa atual
            conexao.desconectar();
            conexao = null;
            inicio.mostrarConversaEmAndamento(null);
        }

        final String nomeFinal = nome;
        final int portaFinal = porta;
        final ConexaoChat nova = new ConexaoChat(this);
        inicio.definirOcupado(true);

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return nova.conectar(servidor, portaFinal, nomeFinal); // bloqueante: fora da EDT
            }

            @Override
            protected void done() {
                inicio.definirOcupado(false);
                try {
                    concluirEntrada(nova, servidor, portaFinal, nomeFinal, get());
                } catch (ExecutionException e) {
                    Throwable causa = e.getCause();
                    inicio.mostrarErro(causa instanceof ConexaoChat.FalhaConexao
                            ? causa.getMessage()
                            : "Erro inesperado: " + causa);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void concluirEntrada(ConexaoChat nova, String servidor, int porta, String nome, List<String> previas) {
        conexao = nova;
        nomeAtual = nome;
        destinoAtual = servidor + ":" + porta;
        transcricao.clear();
        Tema.definirIdentidade(nome, prefs.cor());

        chat.limpar();
        chat.definirSessao(nome, destinoAtual);
        previas.forEach(this::processarLinha); // histórico geral e dos grupos do usuário
        String boasVindas = "Você entrou como " + nome + ". Clique duas vezes em um usuário para "
                + "conversar em privado; use \"Sair do chat\" para desconectar.";
        chat.adicionarAviso(Conversa.GERAL, boasVindas);
        guardar(new AvisoLocal(Conversa.GERAL, boasVindas));

        inicio.limparStatus();
        inicio.mostrarConversaEmAndamento(nome);
        salvarPreferencias(servidor, porta, nome);

        conexao.enviar(Protocolo.MODO_GUI); // o servidor responde com a lista de online e de grupos
        modoUciNoServidor = "padrao"; // toda conexão nova começa no modo padrão
        if (!"padrao".equals(prefs.modoUci())) {
            comando("/uci modo " + prefs.modoUci()); // lembra o jeito da UCI da última vez
        }
        mostrar(CARTAO_CHAT);
        chat.focarEntrada();
    }

    /** Envia o que o usuário digitou na aba {@code conversa}. */
    private void enviar(Conversa conversa, String texto) {
        String t = texto.trim();
        if (t.isEmpty() || conexao == null) {
            return;
        }
        if (t.equalsIgnoreCase("/sair")) {
            sair();
            return;
        }
        if (t.equalsIgnoreCase("/exportar")) { // só o cliente sabe onde gravar o arquivo
            chat.limparEntrada();
            chat.exportar();
            return;
        }
        if (t.startsWith("/")) { // comandos são tratados pelo servidor; a resposta aparece na aba ativa
            comando(t);
            chat.limparEntrada();
            return;
        }

        final Mensagem mensagem;
        try {
            mensagem = montarMensagem(conversa, t); // mesmas regras de negócio do servidor
        } catch (IllegalArgumentException e) {
            chat.adicionarErro(e.getMessage());
            return; // mantém o texto no campo para o usuário corrigir
        }
        if (conexao.enviar(conversa.comandoDeEnvio(t))) {
            String hora = HORA.format(mensagem.getHorario());
            chat.adicionarMensagem(conversa, hora, nomeAtual, mensagem.getConteudo());
            guardar(new Eco(conversa, hora, nomeAtual, mensagem.getConteudo()));
            chat.limparEntrada();
            if (chamaUci(conversa, mensagem.getConteudo())) {
                mostrarUciDigitando();
            }
        } else {
            chat.adicionarErro("Não foi possível enviar: conexão perdida.");
        }
    }

    private Mensagem montarMensagem(Conversa conversa, String texto) {
        return switch (conversa.tipo()) {
            case GERAL -> new Mensagem(nomeAtual, texto);
            case GRUPO -> Mensagem.grupo(conversa.chave(), nomeAtual, texto);
            case PRIVADA -> {
                if (!destinatarioOnline(conversa.titulo())) {
                    throw new IllegalArgumentException("O usuário '" + conversa.titulo() + "' não está online.");
                }
                yield Mensagem.privada(conversa.titulo(), nomeAtual, texto);
            }
        };
    }

    private boolean destinatarioOnline(String nome) {
        List<String> online = chat.online();
        return online.isEmpty() || online.stream().anyMatch(n -> n.equalsIgnoreCase(nome));
    }

    private static boolean chamaUci(Conversa conversa, String texto) {
        if (conversa.tipo() == Conversa.Tipo.PRIVADA) {
            return Usuario.reservado(conversa.titulo());
        }
        return MENCAO_UCI.matcher(texto).find();
    }

    private void comando(String texto) {
        if (texto.startsWith("/uci modo ")) {
            // só lembra modos válidos; o servidor responde com o erro se o usuário digitou algo errado
            br.unicesumar.chat.agente.Personalidade.de(texto.substring("/uci modo ".length()))
                    .map(br.unicesumar.chat.agente.Personalidade::nome)
                    .ifPresent(modo -> {
                        prefs.definirModoUci(modo);
                        if (conexao != null) {
                            modoUciNoServidor = modo;
                        }
                    });
        }
        if (conexao != null) {
            conexao.enviar(texto);
        }
    }

    // ------------------------------------------------------------------------------------
    // "fulano está digitando…"
    // ------------------------------------------------------------------------------------

    /** Chamado a cada tecla: avisa o servidor no máximo a cada 3 s por conversa. */
    private void aoDigitar(Conversa conversa) {
        if (conexao == null || (conversa.tipo() == Conversa.Tipo.PRIVADA && Usuario.reservado(conversa.titulo()))) {
            return; // a UCI não precisa saber
        }
        long agora = relogioNs.getAsLong();
        if (conversa.equals(ultimaConversaDigitando) && agora - ultimoAvisoDigitando < INTERVALO_DIGITANDO_NS) {
            return;
        }
        ultimaConversaDigitando = conversa;
        ultimoAvisoDigitando = agora;
        String alvo = switch (conversa.tipo()) {
            case GERAL -> "geral";
            case GRUPO -> "#" + conversa.chave();
            case PRIVADA -> conversa.titulo();
        };
        conexao.enviar(Protocolo.COMANDO_DIGITANDO + " " + alvo);
    }

    private void mostrarDigitando(Evento.Digitando evento) {
        if (evento.quem().equalsIgnoreCase(nomeAtual)) {
            return;
        }
        Conversa conversa = evento.conversa().equals("geral") ? Conversa.GERAL
                : evento.conversa().startsWith("#") ? Conversa.grupo(evento.conversa())
                : Conversa.privada(evento.quem());
        if (!conversa.equals(chat.conversaAtiva())) {
            return; // só mostra para quem está olhando aquela conversa
        }
        quemDigita = evento.quem();
        chat.mostrarStatus(evento.quem() + " está digitando…");
        if (temporizadorDigitando != null) {
            temporizadorDigitando.stop();
        }
        temporizadorDigitando = new Timer(EXIBIR_DIGITANDO_MS, e -> limparDigitando());
        temporizadorDigitando.setRepeats(false);
        temporizadorDigitando.start();
    }

    private void limparDigitando() {
        if (temporizadorDigitando != null) {
            temporizadorDigitando.stop();
            temporizadorDigitando = null;
        }
        if (quemDigita != null) {
            quemDigita = null;
            chat.mostrarStatus(" ");
        }
    }

    /** Ao abrir uma aba nova pelo usuário, busca no servidor as mensagens anteriores dela. */
    private void pedirHistorico(Conversa conversa) {
        if (conversa.tipo() == Conversa.Tipo.GRUPO) {
            comando("/historico #" + conversa.chave());
        } else if (conversa.tipo() == Conversa.Tipo.PRIVADA) {
            comando("/historico " + conversa.titulo());
        }
    }

    private void sair() {
        if (conexao != null) {
            conexao.desconectar();
            conexao = null;
        }
        limparUciDigitando();
        limparDigitando();
        transcricao.clear();
        chat.limpar();
        inicio.mostrarConversaEmAndamento(null);
        inicio.mostrarInfo("Você saiu do chat.");
        mostrar(CARTAO_INICIO);
    }

    // ------------------------------------------------------------------------------------
    // Eventos da rede (thread de leitura) → EDT
    // ------------------------------------------------------------------------------------

    @Override
    public void aoReceber(String linha) {
        SwingUtilities.invokeLater(() -> processarLinha(linha));
    }

    @Override
    public void aoDesconectar(boolean pedidoPeloUsuario) {
        if (pedidoPeloUsuario) {
            return; // o próprio sair() já cuidou da tela
        }
        SwingUtilities.invokeLater(() -> {
            if (conexao == null) {
                return;
            }
            conexao = null;
            limparUciDigitando();
            limparDigitando();
            transcricao.clear();
            chat.limpar();
            inicio.mostrarConversaEmAndamento(null);
            inicio.mostrarErro("A conexão com o servidor foi encerrada.");
            mostrar(CARTAO_INICIO);
        });
    }

    /** Decide em que aba cada linha do servidor aparece. */
    private void processarLinha(String linha) {
        Evento evento = Interpretador.interpretar(linha);
        if (!(evento instanceof Evento.Digitando)) { // "digitando" é passageiro: não vale guardar
            guardar(linha);
        }
        if (evento instanceof Evento.MensagemGeral geral) {
            mostrarMensagem(Conversa.GERAL, geral.hora(), geral.remetente(), geral.texto());
        } else if (evento instanceof Evento.MensagemGrupo grupo) {
            mostrarMensagem(Conversa.grupo(grupo.grupo()), grupo.hora(), grupo.remetente(), grupo.texto());
        } else if (evento instanceof Evento.MensagemPrivada privada) {
            String outro = privada.de().equalsIgnoreCase(nomeAtual) ? privada.para() : privada.de();
            mostrarMensagem(Conversa.privada(outro), privada.hora(), privada.de(), privada.texto());
        } else if (evento instanceof Evento.AvisoGeral aviso) {
            chat.adicionarAviso(Conversa.GERAL, aviso.texto());
        } else if (evento instanceof Evento.AvisoGrupo aviso) {
            chat.adicionarAviso(Conversa.grupo(aviso.grupo()), aviso.texto());
        } else if (evento instanceof Evento.CabecalhoGeral cabecalho) {
            chat.adicionarCabecalho(Conversa.GERAL, cabecalho.texto());
        } else if (evento instanceof Evento.CabecalhoGrupo cabecalho) {
            chat.adicionarCabecalho(Conversa.grupo(cabecalho.grupo()), cabecalho.texto());
        } else if (evento instanceof Evento.CabecalhoPrivado cabecalho) {
            chat.adicionarCabecalho(Conversa.privada(cabecalho.com()), cabecalho.texto());
        } else if (evento instanceof Evento.Usuarios usuarios) {
            chat.atualizarOnline(usuarios.nomes());
        } else if (evento instanceof Evento.Grupos grupos) {
            chat.atualizarGrupos(grupos.meus(), grupos.todos());
        } else if (evento instanceof Evento.Status status) {
            chat.atualizarStatus(status.porUsuario());
        } else if (evento instanceof Evento.Digitando digitando) {
            mostrarDigitando(digitando);
        } else if (evento instanceof Evento.Erro erro) {
            limparUciDigitando();
            chat.adicionarErro(erro.texto());
        } else if (evento instanceof Evento.Outra outra) {
            chat.adicionarTexto(outra.texto());
        }
    }

    private void mostrarMensagem(Conversa conversa, String hora, String remetente, String texto) {
        if (Usuario.reservado(remetente)) {
            limparUciDigitando(); // a UCI respondeu
        }
        if (remetente.equalsIgnoreCase(quemDigita)) {
            limparDigitando(); // a mensagem chegou: não está mais "digitando"
        }
        chat.adicionarMensagem(conversa, hora, remetente, texto);
    }

    // ------------------------------------------------------------------------------------
    // "UCI está digitando…"
    // ------------------------------------------------------------------------------------

    private void mostrarUciDigitando() {
        chat.mostrarStatus("UCI está digitando…");
        if (temporizadorUci != null) {
            temporizadorUci.stop();
        }
        temporizadorUci = new Timer(ESPERA_MAXIMA_UCI_MS, e -> chat.mostrarStatus(" "));
        temporizadorUci.setRepeats(false);
        temporizadorUci.start();
    }

    private void limparUciDigitando() {
        if (temporizadorUci != null) {
            temporizadorUci.stop();
            temporizadorUci = null;
        }
        chat.mostrarStatus(" ");
    }

    // ------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------

    private void mostrar(String cartao) {
        cartaoAtual = cartao;
        cartoes.show(this, cartao);
        if (CARTAO_INICIO.equals(cartao)) {
            inicio.focarNome();
        } else {
            chat.focarEntrada();
        }
    }

    private void salvarPreferencias(String servidor, int porta, String nome) {
        try {
            preferencias.put("servidor", servidor);
            preferencias.put("porta", String.valueOf(porta));
            preferencias.put("nome", nome);
        } catch (RuntimeException ignorada) {
            // preferências são só uma comodidade
        }
    }

    // ------------------------------------------------------------------------------------
    // Acesso para testes no mesmo pacote
    // ------------------------------------------------------------------------------------

    TelaInicio telaInicio() {
        return inicio;
    }

    TelaChat telaChat() {
        return chat;
    }

    Preferencias preferenciasDoUsuario() {
        return prefs;
    }

    boolean conectado() {
        return conexao != null;
    }
}
