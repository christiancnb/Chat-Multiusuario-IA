package br.unicesumar.chat.server;

import br.unicesumar.chat.agente.Personalidade;
import br.unicesumar.chat.agente.ServicoUci;
import br.unicesumar.chat.model.Grupo;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.StatusUsuario;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.GrupoRepository;
import br.unicesumar.chat.persistence.GrupoRepositoryMemoria;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.PersistenciaException;
import br.unicesumar.chat.protocolo.Protocolo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Coração do chat: mantém a lista (sincronizada) de clientes conectados, os grupos e a
 * ligação com a UCI; persiste cada mensagem e a entrega a quem deve recebê-la.
 *
 * <h2>Três tipos de conversa</h2>
 * <ul>
 *   <li><b>geral</b>: todos os conectados;</li>
 *   <li><b>grupo</b>: só os membros do grupo que estiverem online (os grupos e seus membros
 *       ficam gravados no banco, então a participação sobrevive à desconexão);</li>
 *   <li><b>privada</b>: um único destinatário, que precisa estar online. Conversar com a
 *       "UCI" aciona o agente de IA.</li>
 * </ul>
 *
 * <h2>Concorrência</h2>
 * A lista de clientes, os grupos e o banco são recursos compartilhados por todas as
 * threads {@link ClienteHandler} (e pelas threads da UCI). A lista é uma
 * {@link Collections#synchronizedList(List)}, e as operações compostas
 * (checar nome + adicionar, salvar + difundir, iterar) são feitas dentro de
 * {@code synchronized (clientes)}, o idioma documentado para esse caso. Esse <b>único lock</b>
 * protege também o {@link RegistroGrupos}. Como entrar, sair, criar grupo e publicar usam o
 * mesmo lock:
 * <ul>
 *   <li>todos os clientes veem as mensagens na mesma ordem em que foram gravadas;</li>
 *   <li>um cliente que entra recebe o histórico e passa a receber as novas
 *       mensagens sem perder nem duplicar nenhuma;</li>
 *   <li>nunca se itera sobre a lista enquanto outra thread a modifica;</li>
 *   <li>duas threads não conseguem criar o mesmo grupo nem registrar o mesmo nome.</li>
 * </ul>
 * Para que o lock não seja segurado por muito tempo, {@link Destinatario#enviar} é não bloqueante
 * (cada cliente tem sua própria fila de saída) e a chamada à IA é feita fora do lock, em threads
 * do {@link ServicoUci}; a resposta volta tomando o lock só para publicar.
 */
public class SalaChat {

    private static final Logger LOG = Logger.getLogger(SalaChat.class.getName());
    public static final int HISTORICO_PADRAO = 20;

    /** "@uci" como palavra inteira, em qualquer posição do texto e sem diferenciar maiúsculas. */
    private static final Pattern MENCAO_UCI =
            Pattern.compile("(?<![\\p{L}\\p{N}_])@uci(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final List<Destinatario> clientes = Collections.synchronizedList(new ArrayList<>());
    private final MensagemRepository repositorio;
    private final RegistroGrupos grupos;
    private final ServicoUci uci; // null = UCI desativada
    private final int tamanhoHistorico;

    /** Status e estilo da UCI por usuário (chave canônica). Acessados só sob o lock de {@code clientes}. */
    private final Map<String, StatusUsuario> status = new HashMap<>();
    private final Map<String, Personalidade> personalidades = new HashMap<>();
    private volatile ModeradorDeMensagens moderador = ModeradorDeMensagens.NENHUM;

    public static final int LIMITE_BUSCA = 20;
    private static final int CANDIDATAS_BUSCA = 300;
    private static final int LIMITE_RESUMO = 50;

    public SalaChat(MensagemRepository repositorio) {
        this(repositorio, HISTORICO_PADRAO);
    }

    /** Sala sem persistência de grupos (ficam só em memória) e sem UCI. */
    public SalaChat(MensagemRepository repositorio, int tamanhoHistorico) {
        this(repositorio, new GrupoRepositoryMemoria(), null, tamanhoHistorico);
    }

    /**
     * @param uci serviço da assistente; {@code null} desativa a UCI
     */
    public SalaChat(MensagemRepository repositorio, GrupoRepository grupoRepositorio, ServicoUci uci,
                    int tamanhoHistorico) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.grupos = new RegistroGrupos(Objects.requireNonNull(grupoRepositorio));
        this.uci = uci;
        if (tamanhoHistorico <= 0) {
            throw new IllegalArgumentException("O tamanho do histórico deve ser positivo.");
        }
        this.tamanhoHistorico = tamanhoHistorico;
    }

    // ------------------------------------------------------------------------------------
    // Presença
    // ------------------------------------------------------------------------------------

    /**
     * Registra um novo cliente: recusa nomes já em uso ou reservados (sem diferenciar
     * maiúsculas), envia o histórico geral e o de cada grupo do usuário, e avisa os demais.
     *
     * @return {@code false} se o nome já está em uso (ou é reservado)
     */
    public boolean entrar(Destinatario novo) {
        synchronized (clientes) {
            String nome = novo.getNome();
            if (Usuario.reservado(nome) || nomeEmUso(nome)) {
                return false;
            }
            enviarHistoricoGeral(novo);
            for (Grupo g : grupos.gruposDe(nome)) {
                enviarHistoricoGrupo(novo, g, true);
            }
            clientes.add(novo);
            difundir("* " + nome + " entrou no chat.", novo);
            difundirUsuarios();
            return true;
        }
    }

    /** Remove o cliente (se ainda estiver na sala) e avisa os demais. A participação em grupos é mantida. */
    public boolean sair(Destinatario cliente) {
        synchronized (clientes) {
            if (!clientes.remove(cliente)) {
                return false;
            }
            aoSairDaSala(cliente);
            return true;
        }
    }

    /** Nomes dos clientes conectados, em ordem alfabética (sem a UCI). */
    public List<String> listarNomes() {
        synchronized (clientes) {
            return clientes.stream()
                    .map(Destinatario::getNome)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    /** Quem aparece como "online": os conectados e, se estiver ativa, a UCI. */
    public List<String> listarUsuarios() {
        synchronized (clientes) {
            List<String> nomes = new ArrayList<>(listarNomes());
            if (uci != null) {
                nomes.add(Usuario.NOME_UCI);
                nomes.sort(String.CASE_INSENSITIVE_ORDER);
            }
            return nomes;
        }
    }

    public int totalConectados() {
        return clientes.size();
    }

    // ------------------------------------------------------------------------------------
    // Mensagens
    // ------------------------------------------------------------------------------------

    /**
     * Mensagem para a sala geral: persiste e repassa a todos os demais. Se o texto contiver
     * "@UCI", a assistente também responde para todos.
     * Se o banco falhar, a conversa continua (o erro é registrado no log).
     *
     * @throws IllegalArgumentException se o conteúdo violar as regras de {@link Mensagem}
     * @throws ChatException se a UCI estiver ocupada
     */
    public Mensagem publicar(Destinatario remetente, String conteudo) {
        synchronized (clientes) {
            conteudo = moderar(remetente, conteudo);
            Mensagem registrada = persistir(new Mensagem(remetente.getNome(), conteudo));
            difundir(registrada.formatar(), remetente);
            if (mencionaUci(registrada.getConteudo())) {
                acionarUci(remetente, registrada, "geral", remetente.getNome() + ": " + registrada.getConteudo());
            }
            return registrada;
        }
    }

    /**
     * Mensagem privada. O destinatário precisa estar online, exceto a "UCI", que responde via IA.
     *
     * @throws ChatException destinatário offline, o próprio remetente ou UCI indisponível/ocupada
     * @throws IllegalArgumentException conteúdo inválido
     */
    public Mensagem publicarPrivada(Destinatario remetente, String paraNome, String conteudo) {
        synchronized (clientes) {
            String alvoNome = paraNome == null ? "" : paraNome.trim();
            if (alvoNome.equalsIgnoreCase(remetente.getNome())) {
                throw new ChatException("Você não pode enviar mensagem privada para si mesmo.");
            }
            conteudo = moderar(remetente, conteudo);
            if (Usuario.reservado(alvoNome)) {
                return conversarComUci(remetente, conteudo);
            }
            Destinatario alvo = buscarOnline(alvoNome);
            if (alvo == null) {
                throw new ChatException("O usuário '" + alvoNome + "' não está online.");
            }
            Mensagem registrada = persistir(Mensagem.privada(alvo.getNome(), remetente.getNome(), conteudo));
            if (!alvo.enviar(registrada.formatar())) {
                removerInalcancavel(alvo);
                throw new ChatException("O usuário '" + alvoNome + "' não está mais online.");
            }
            return registrada;
        }
    }

    /**
     * Mensagem para um grupo: só quem é membro pode enviar, e só os membros online recebem.
     * "@UCI" no texto chama a assistente, que responde ao grupo.
     *
     * @throws ChatException grupo inexistente, remetente não é membro ou UCI ocupada
     */
    public Mensagem publicarNoGrupo(Destinatario remetente, String nomeGrupo, String conteudo) {
        synchronized (clientes) {
            Grupo grupo = grupos.exigir(nomeGrupo);
            exigirMembro(grupo, remetente);
            conteudo = moderar(remetente, conteudo);
            Mensagem registrada = persistir(Mensagem.grupo(grupo.getNome(), remetente.getNome(), conteudo));
            difundir(registrada.formatar(), remetente, membroDe(grupo));
            if (mencionaUci(registrada.getConteudo())) {
                acionarUci(remetente, registrada, "grupo:" + grupo.getNome(),
                        remetente.getNome() + ": " + registrada.getConteudo());
            }
            return registrada;
        }
    }

    // ------------------------------------------------------------------------------------
    // Grupos
    // ------------------------------------------------------------------------------------

    /** Cria o grupo; o criador é o primeiro membro. */
    public Grupo criarGrupo(Destinatario criador, String nome) {
        synchronized (clientes) {
            Grupo grupo = grupos.criar(nome, criador.getNome());
            enviarOuRemover(criador, Protocolo.avisoGrupo(grupo.getNome(), "Grupo criado. Você é o primeiro membro."));
            difundirGrupos();
            return grupo;
        }
    }

    /** Entra em um grupo existente (os grupos são abertos) e recebe o histórico dele. */
    public Grupo entrarNoGrupo(Destinatario usuario, String nome) {
        synchronized (clientes) {
            Grupo grupo = grupos.exigir(nome);
            if (grupo.contem(usuario.getNome())) {
                throw new ChatException("Você já é membro do grupo #" + grupo.getNome() + ".");
            }
            grupos.adicionarMembro(grupo, usuario.getNome());
            enviarOuRemover(usuario, Protocolo.avisoGrupo(grupo.getNome(), "Você entrou no grupo."));
            enviarHistoricoGrupo(usuario, grupo, true);
            difundir(Protocolo.avisoGrupo(grupo.getNome(), usuario.getNome() + " entrou no grupo."),
                    usuario, membroDe(grupo));
            difundirGrupos();
            return grupo;
        }
    }

    /** Sai do grupo (o grupo continua existindo, mesmo vazio). */
    public void sairDoGrupo(Destinatario usuario, String nome) {
        synchronized (clientes) {
            Grupo grupo = grupos.exigir(nome);
            exigirMembro(grupo, usuario);
            grupos.removerMembro(grupo, usuario.getNome());
            enviarOuRemover(usuario, Protocolo.avisoGrupo(grupo.getNome(), "Você saiu do grupo."));
            difundir(Protocolo.avisoGrupo(grupo.getNome(), usuario.getNome() + " saiu do grupo."),
                    usuario, membroDe(grupo));
            difundirGrupos();
        }
    }

    public List<String> nomesDosGrupos() {
        synchronized (clientes) {
            return grupos.nomes();
        }
    }

    public List<String> nomesDosGruposDe(String usuario) {
        synchronized (clientes) {
            return grupos.nomesDe(usuario);
        }
    }

    /** Membros do grupo (nomes em minúsculas, em ordem alfabética). */
    public List<String> membrosDoGrupo(String nome) {
        synchronized (clientes) {
            return new ArrayList<>(grupos.exigir(nome).getMembros());
        }
    }

    // ------------------------------------------------------------------------------------
    // Históricos e estado para clientes gráficos
    // ------------------------------------------------------------------------------------

    /** Envia ao usuário as últimas mensagens do grupo (ele precisa ser membro). */
    public void enviarHistoricoDoGrupo(Destinatario usuario, String nome) {
        synchronized (clientes) {
            Grupo grupo = grupos.exigir(nome);
            exigirMembro(grupo, usuario);
            enviarHistoricoGrupo(usuario, grupo, false);
        }
    }

    /** Envia ao usuário as últimas mensagens privadas trocadas com {@code outro}. */
    public void enviarHistoricoPrivado(Destinatario usuario, String outro) {
        synchronized (clientes) {
            String alvo = outro == null ? "" : outro.trim();
            if (!Usuario.nomeValido(alvo) && !Usuario.reservado(alvo)) {
                throw new IllegalArgumentException("Informe um nome de usuário válido.");
            }
            List<Mensagem> historico = buscarSeguro(
                    () -> repositorio.buscarUltimasPrivadas(usuario.getNome(), alvo, tamanhoHistorico));
            if (historico.isEmpty()) {
                enviarOuRemover(usuario, Protocolo.cabecalhoPrivado(alvo, "nenhuma mensagem anterior"));
                return;
            }
            enviarOuRemover(usuario, Protocolo.cabecalhoPrivado(alvo, descricaoDasUltimas(historico.size())));
            for (Mensagem m : historico) {
                usuario.enviar(m.formatar());
            }
            enviarOuRemover(usuario, Protocolo.cabecalhoPrivado(alvo, "fim do histórico"));
        }
    }

    /** Envia a um cliente gráfico a lista de online e a de grupos (usado ao ativar o modo gráfico). */
    public void enviarEstado(Destinatario cliente) {
        synchronized (clientes) {
            enviarOuRemover(cliente, Protocolo.usuarios(listarUsuarios()));
            enviarOuRemover(cliente, Protocolo.grupos(grupos.nomesDe(cliente.getNome()), grupos.nomes()));
            if (!status.isEmpty()) {
                enviarOuRemover(cliente, Protocolo.status(statusVisiveis()));
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // UCI
    // ------------------------------------------------------------------------------------

    private Mensagem conversarComUci(Destinatario remetente, String conteudo) {
        if (uci == null) {
            throw new ChatException("A UCI não está disponível neste servidor.");
        }
        Mensagem registrada = persistir(Mensagem.privada(Usuario.NOME_UCI, remetente.getNome(), conteudo));
        acionarUci(remetente, registrada, conversaPrivadaComUci(remetente.getNome()), registrada.getConteudo());
        return registrada;
    }

    private static String conversaPrivadaComUci(String usuario) {
        return "privado:" + Usuario.chave(usuario);
    }

    private static boolean mencionaUciNoTexto(String texto) {
        return MENCAO_UCI.matcher(texto).find();
    }

    private boolean mencionaUci(String texto) {
        return uci != null && mencionaUciNoTexto(texto);
    }

    /** Enfileira a pergunta (não bloqueia). A resposta chega depois, por {@link #publicarRespostaDaUci}. */
    private void acionarUci(Destinatario autor, Mensagem origem, String conversa, String turno) {
        String autorNome = autor.getNome();
        ServicoUci.Resultado resultado = uci.perguntar(conversa, turno, personalidadeDe(autorNome).instrucao(), new ServicoUci.Ouvinte() {
            @Override
            public void aoResponder(List<String> partes) {
                publicarRespostaDaUci(origem, autorNome, partes);
            }

            @Override
            public void aoFalhar(String mensagemAmigavel) {
                synchronized (clientes) {
                    enviarAoUsuario(autorNome, "! " + mensagemAmigavel);
                }
            }
        });
        if (resultado == ServicoUci.Resultado.OCUPADO) {
            throw new ChatException("A UCI ainda está respondendo à pergunta anterior desta conversa. Aguarde um instante.");
        }
        if (resultado == ServicoUci.Resultado.SOBRECARREGADO) {
            throw new ChatException("A UCI está muito ocupada agora. Tente novamente em instantes.");
        }
    }

    /** Chamado pela thread da UCI: toma o lock da sala só para gravar e entregar a resposta. */
    private void publicarRespostaDaUci(Mensagem origem, String autorNome, List<String> partes) {
        synchronized (clientes) {
            for (String parte : partes) {
                Mensagem resposta = switch (origem.getTipo()) {
                    case GERAL -> new Mensagem(Usuario.NOME_UCI, parte);
                    case GRUPO -> Mensagem.grupo(origem.getDestino(), Usuario.NOME_UCI, parte);
                    case PRIVADA -> Mensagem.privada(autorNome, Usuario.NOME_UCI, parte);
                };
                resposta = persistir(resposta);
                String linha = resposta.formatar();
                switch (origem.getTipo()) {
                    case GERAL -> difundir(linha, null);
                    case GRUPO -> difundir(linha, null, membroDe(grupos.exigir(origem.getDestino())));
                    case PRIVADA -> enviarAoUsuario(autorNome, linha);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Status, "digitando", busca, moderação, estilo da UCI e administração de grupos
    // ------------------------------------------------------------------------------------

    /** Troca a regra de moderação (adaptador da porta {@link ModeradorDeMensagens}). */
    public void usarModerador(ModeradorDeMensagens novo) {
        this.moderador = Objects.requireNonNull(novo);
    }

    private String moderar(Destinatario autor, String texto) {
        if (texto == null) {
            return null;
        }
        String ajustado = moderador.moderar(texto);
        if (!ajustado.equals(texto)) {
            enviarOuRemover(autor, "! Sua mensagem tinha palavras ofensivas e foi ajustada antes de ser enviada.");
        }
        return ajustado;
    }

    /** Muda como o usuário aparece para os outros e avisa os clientes gráficos. */
    public void definirStatus(Destinatario usuario, StatusUsuario novo) {
        synchronized (clientes) {
            String chave = Usuario.chave(usuario.getNome());
            if (novo == StatusUsuario.DISPONIVEL) {
                status.remove(chave);
            } else {
                status.put(chave, novo);
            }
            difundirStatus();
        }
    }

    public StatusUsuario statusDe(String nome) {
        synchronized (clientes) {
            return status.getOrDefault(Usuario.chave(nome), StatusUsuario.DISPONIVEL);
        }
    }

    /** Status de quem está online e NÃO está disponível (usuário em minúsculas -> rótulo). */
    private Map<String, String> statusVisiveis() {
        Map<String, String> visiveis = new LinkedHashMap<>();
        for (String nome : listarNomes()) {
            StatusUsuario s = status.get(Usuario.chave(nome));
            if (s != null) {
                visiveis.put(Usuario.chave(nome), s.rotulo());
            }
        }
        return visiveis;
    }

    private void difundirStatus() {
        String linha = Protocolo.status(statusVisiveis());
        enviarAosClientesGraficos(d -> linha);
    }

    /**
     * Repassa "fulano está digitando" a quem está na mesma conversa (só clientes gráficos).
     * É silencioso: alvo inválido ou inexistente é simplesmente ignorado.
     *
     * @param alvo "geral", "#grupo" ou o nome do usuário (conversa privada)
     */
    public void avisarDigitando(Destinatario autor, String alvo) {
        synchronized (clientes) {
            String a = alvo == null ? "" : alvo.trim();
            String quem = autor.getNome();
            String linha;
            Predicate<Destinatario> publico;
            if (a.equalsIgnoreCase("geral")) {
                linha = Protocolo.digitando(quem, "geral");
                publico = d -> true;
            } else if (a.startsWith("#")) {
                Grupo grupo;
                try {
                    grupo = grupos.exigir(a);
                } catch (ChatException e) {
                    return;
                }
                if (!grupo.contem(quem)) {
                    return;
                }
                linha = Protocolo.digitando(quem, "#" + grupo.getNome());
                publico = membroDe(grupo);
            } else {
                linha = Protocolo.digitando(quem, quem);
                publico = d -> d.getNome().equalsIgnoreCase(a);
            }
            for (Destinatario d : new ArrayList<>(clientes)) {
                if (d != autor && d.aceitaControle() && publico.test(d)) {
                    d.enviar(linha); // se falhar, a próxima entrega de verdade remove o cliente
                }
            }
        }
    }

    /**
     * Busca o texto nas mensagens que o usuário PODE ver (sala geral, grupos dos quais é membro e
     * conversas privadas dele) e envia as {@value #LIMITE_BUSCA} mais recentes.
     *
     * @throws IllegalArgumentException se o termo tiver menos de 2 caracteres
     */
    public void buscar(Destinatario usuario, String termo) {
        String t = termo == null ? "" : termo.trim();
        if (t.length() < 2) {
            throw new IllegalArgumentException("Uso: /buscar <texto> (mínimo de 2 caracteres)");
        }
        synchronized (clientes) {
            List<Mensagem> visiveis = buscarSeguro(() -> repositorio.buscarPorTexto(t, CANDIDATAS_BUSCA)).stream()
                    .filter(m -> visivelPara(m, usuario.getNome()))
                    .toList();
            List<Mensagem> recentes = visiveis.subList(Math.max(0, visiveis.size() - LIMITE_BUSCA), visiveis.size());
            if (recentes.isEmpty()) {
                enviarOuRemover(usuario, "Busca por \"" + t + "\": nenhum resultado.");
                return;
            }
            enviarOuRemover(usuario, "Busca por \"" + t + "\": " + recentes.size()
                    + (recentes.size() == 1 ? " resultado" : " resultados") + " (mais recentes por último)");
            for (Mensagem m : recentes) {
                usuario.enviar("  > " + m.formatar());
            }
        }
    }

    private boolean visivelPara(Mensagem m, String usuario) {
        return switch (m.getTipo()) {
            case GERAL -> true;
            case PRIVADA -> m.getRemetente().equalsIgnoreCase(usuario) || m.getDestino().equalsIgnoreCase(usuario);
            case GRUPO -> {
                try {
                    yield grupos.exigir(m.getDestino()).contem(usuario);
                } catch (ChatException e) {
                    yield false;
                }
            }
        };
    }

    // ---- estilo da UCI e utilidades ----

    public void definirPersonalidade(Destinatario usuario, Personalidade nova) {
        synchronized (clientes) {
            if (nova == Personalidade.PADRAO) {
                personalidades.remove(Usuario.chave(usuario.getNome()));
            } else {
                personalidades.put(Usuario.chave(usuario.getNome()), nova);
            }
        }
    }

    public Personalidade personalidadeDe(String nome) {
        synchronized (clientes) {
            return personalidades.getOrDefault(Usuario.chave(nome), Personalidade.PADRAO);
        }
    }

    public boolean uciAtiva() {
        return uci != null;
    }

    /**
     * Texto das últimas mensagens de uma conversa que o usuário pode ver, para a UCI resumir.
     *
     * @param alvo "geral" (ou vazio), "#grupo" ou o nome de outro usuário
     * @throws ChatException sem permissão ou sem mensagens
     */
    public String textoParaResumo(Destinatario usuario, String alvo) {
        synchronized (clientes) {
            String a = alvo == null ? "" : alvo.trim();
            List<Mensagem> mensagens;
            if (a.isEmpty() || a.equalsIgnoreCase("geral")) {
                mensagens = buscarSeguro(() -> repositorio.buscarUltimas(LIMITE_RESUMO));
            } else if (a.startsWith("#")) {
                Grupo grupo = grupos.exigir(a);
                exigirMembro(grupo, usuario);
                mensagens = buscarSeguro(() -> repositorio.buscarUltimasDoGrupo(grupo.getNome(), LIMITE_RESUMO));
            } else {
                mensagens = buscarSeguro(() -> repositorio.buscarUltimasPrivadas(usuario.getNome(), a, LIMITE_RESUMO));
            }
            if (mensagens.isEmpty()) {
                throw new ChatException("Não há mensagens para resumir nessa conversa.");
            }
            StringBuilder texto = new StringBuilder();
            for (Mensagem m : mensagens) {
                texto.append(m.getRemetente()).append(": ").append(m.getConteudo()).append(" | ");
            }
            return texto.toString();
        }
    }

    /**
     * Pede à UCI uma tarefa de utilidade (resumir, traduzir, explicar). A resposta chega só para
     * quem pediu, como mensagem privada da UCI.
     */
    public void pedirUtilidadeUci(Destinatario usuario, String pedido) {
        synchronized (clientes) {
            if (uci == null) {
                throw new ChatException("A UCI não está disponível neste servidor.");
            }
            Mensagem origem = Mensagem.privada(Usuario.NOME_UCI, usuario.getNome(), "(pedido de utilidade)");
            acionarUci(usuario, origem, "util:" + Usuario.chave(usuario.getNome()), pedido);
        }
    }

    // ---- administração de grupo ----

    /** O criador do grupo remove outro membro. */
    public void removerDoGrupo(Destinatario admin, String nomeGrupo, String alvoNome) {
        synchronized (clientes) {
            Grupo grupo = grupos.exigir(nomeGrupo);
            if (!grupo.getCriador().equalsIgnoreCase(admin.getNome())) {
                throw new ChatException("Só o criador do grupo #" + grupo.getNome() + " pode remover membros.");
            }
            String alvo = alvoNome == null ? "" : alvoNome.trim();
            if (alvo.equalsIgnoreCase(admin.getNome())) {
                throw new ChatException("Você é o criador do grupo. Para sair use /grupo sair " + grupo.getNome() + ".");
            }
            if (!grupo.contem(alvo)) {
                throw new ChatException("'" + alvo + "' não é membro do grupo #" + grupo.getNome() + ".");
            }
            grupos.removerMembro(grupo, alvo);
            Destinatario removido = buscarOnline(alvo);
            if (removido != null) {
                enviarOuRemover(removido, Protocolo.avisoGrupo(grupo.getNome(),
                        "Você foi removido do grupo por " + admin.getNome() + "."));
            }
            difundir(Protocolo.avisoGrupo(grupo.getNome(), alvo + " foi removido do grupo por "
                    + admin.getNome() + "."), null, membroDe(grupo));
            difundirGrupos();
        }
    }

    // ------------------------------------------------------------------------------------
    // Internos: chamados sempre com o lock de "clientes" já adquirido
    // ------------------------------------------------------------------------------------

    private boolean nomeEmUso(String nome) {
        return clientes.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
    }

    private Destinatario buscarOnline(String nome) {
        for (Destinatario d : clientes) {
            if (d.getNome().equalsIgnoreCase(nome)) {
                return d;
            }
        }
        return null;
    }

    private static void exigirMembro(Grupo grupo, Destinatario usuario) {
        if (!grupo.contem(usuario.getNome())) {
            throw new ChatException("Você não é membro do grupo #" + grupo.getNome()
                    + ". Use /grupo entrar " + grupo.getNome() + ".");
        }
    }

    private static Predicate<Destinatario> membroDe(Grupo grupo) {
        return d -> grupo.contem(d.getNome());
    }

    private Mensagem persistir(Mensagem mensagem) {
        try {
            return repositorio.salvar(mensagem);
        } catch (PersistenciaException e) {
            LOG.log(Level.SEVERE, "Não foi possível gravar a mensagem; ela será apenas retransmitida.", e);
            return mensagem;
        }
    }

    private List<Mensagem> buscarSeguro(Supplier<List<Mensagem>> consulta) {
        try {
            return consulta.get();
        } catch (PersistenciaException e) {
            LOG.log(Level.SEVERE, "Não foi possível carregar o histórico.", e);
            return List.of();
        }
    }

    private void enviarHistoricoGeral(Destinatario novo) {
        List<Mensagem> historico = buscarSeguro(() -> repositorio.buscarUltimas(tamanhoHistorico));
        if (historico.isEmpty()) {
            novo.enviar("--- Nenhuma mensagem anterior ---");
            return;
        }
        novo.enviar(historico.size() == 1
                ? "--- Última mensagem ---"
                : "--- Últimas " + historico.size() + " mensagens ---");
        for (Mensagem m : historico) {
            novo.enviar(m.formatar());
        }
        novo.enviar("--- Fim do histórico ---");
    }

    /** @param omitirSeVazio não envia nada (nem cabeçalho) quando o grupo ainda não tem mensagens */
    private void enviarHistoricoGrupo(Destinatario destino, Grupo grupo, boolean omitirSeVazio) {
        List<Mensagem> historico = buscarSeguro(
                () -> repositorio.buscarUltimasDoGrupo(grupo.getNome(), tamanhoHistorico));
        if (historico.isEmpty()) {
            if (!omitirSeVazio) {
                destino.enviar(Protocolo.cabecalhoGrupo(grupo.getNome(), "nenhuma mensagem anterior"));
            }
            return;
        }
        destino.enviar(Protocolo.cabecalhoGrupo(grupo.getNome(), descricaoDasUltimas(historico.size())));
        for (Mensagem m : historico) {
            destino.enviar(m.formatar());
        }
        destino.enviar(Protocolo.cabecalhoGrupo(grupo.getNome(), "fim do histórico"));
    }

    private static String descricaoDasUltimas(int n) {
        return n == 1 ? "última mensagem" : "últimas " + n + " mensagens";
    }

    private void difundir(String linha, Destinatario exceto) {
        difundir(linha, exceto, d -> true);
    }

    /** Envia a linha a quem passar no filtro (menos {@code exceto}); quem não aceitar é removido e a saída é anunciada. */
    private void difundir(String linha, Destinatario exceto, Predicate<Destinatario> alvo) {
        List<Destinatario> inalcancaveis = new ArrayList<>();
        for (Destinatario d : new ArrayList<>(clientes)) { // cópia: a lista pode mudar durante o laço
            if (d != exceto && alvo.test(d) && !d.enviar(linha)) {
                inalcancaveis.add(d);
            }
        }
        inalcancaveis.forEach(this::removerInalcancavel);
    }

    private void enviarOuRemover(Destinatario destino, String linha) {
        if (!destino.enviar(linha)) {
            removerInalcancavel(destino);
        }
    }

    private void enviarAoUsuario(String nome, String linha) {
        Destinatario d = buscarOnline(nome);
        if (d != null) {
            enviarOuRemover(d, linha);
        }
    }

    private void removerInalcancavel(Destinatario d) {
        if (clientes.remove(d)) {
            aoSairDaSala(d);
        }
    }

    /** Efeitos de um cliente ter saído (ele já foi tirado da lista). */
    private void aoSairDaSala(Destinatario cliente) {
        if (uci != null) {
            uci.esquecer(conversaPrivadaComUci(cliente.getNome())); // não deixa o contexto vazar para quem usar o nome depois
        }
        personalidades.remove(Usuario.chave(cliente.getNome()));
        boolean tinhaStatus = status.remove(Usuario.chave(cliente.getNome())) != null;
        difundir("* " + cliente.getNome() + " saiu do chat.", null);
        difundirUsuarios();
        if (tinhaStatus) {
            difundirStatus();
        }
    }

    // ---- linhas de controle (só para clientes gráficos) ----

    private void difundirUsuarios() {
        String linha = Protocolo.usuarios(listarUsuarios());
        enviarAosClientesGraficos(d -> linha);
    }

    private void difundirGrupos() {
        List<String> todos = grupos.nomes();
        enviarAosClientesGraficos(d -> Protocolo.grupos(grupos.nomesDe(d.getNome()), todos));
    }

    private void enviarAosClientesGraficos(Function<Destinatario, String> linhaPara) {
        List<Destinatario> inalcancaveis = new ArrayList<>();
        for (Destinatario d : new ArrayList<>(clientes)) {
            if (d.aceitaControle() && !d.enviar(linhaPara.apply(d))) {
                inalcancaveis.add(d);
            }
        }
        inalcancaveis.forEach(this::removerInalcancavel);
    }
}
