package br.unicesumar.chat.server;

import br.unicesumar.chat.agente.AgenteAnthropic;
import br.unicesumar.chat.agente.AgenteCompativel;
import br.unicesumar.chat.agente.AgenteIA;
import br.unicesumar.chat.agente.AgenteLocal;
import br.unicesumar.chat.agente.ServicoUci;
import br.unicesumar.chat.persistence.ConexaoFactory;
import br.unicesumar.chat.persistence.JdbcGrupoRepository;
import br.unicesumar.chat.persistence.JdbcMensagemRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ponto de entrada do servidor: mantém um socket de escuta e, para cada
 * conexão aceita, dispara uma {@link ClienteHandler} em uma nova thread.
 */
public class Servidor {

    private static final Logger LOG = Logger.getLogger(Servidor.class.getName());
    public static final int PORTA_PADRAO = 5000;

    private final int porta;
    private final SalaChat sala;
    private final AtomicInteger contador = new AtomicInteger();

    private volatile ServerSocket socketEscuta;
    private volatile boolean ativo;

    public Servidor(int porta, SalaChat sala) {
        this.porta = porta;
        this.sala = sala;
    }

    /** Abre o socket de escuta (use porta 0 para o sistema escolher uma livre). */
    public void abrir() throws IOException {
        socketEscuta = new ServerSocket(porta);
        ativo = true;
        LOG.info("Servidor ouvindo na porta " + getPortaLocal());
    }

    public int getPortaLocal() {
        return socketEscuta.getLocalPort();
    }

    /** Laço principal: aceita conexões até {@link #parar()} ser chamado. */
    public void aceitarConexoes() {
        while (ativo) {
            try {
                Socket cliente = socketEscuta.accept();
                Thread thread = new Thread(new ClienteHandler(cliente, sala),
                        "cliente-" + contador.incrementAndGet());
                thread.setDaemon(true);
                thread.start();
            } catch (IOException e) {
                if (!ativo || socketEscuta.isClosed()) {
                    break;
                }
                LOG.log(Level.WARNING, "Falha ao aceitar conexão: " + e.getMessage());
            }
        }
    }

    public void iniciar() throws IOException {
        abrir();
        aceitarConexoes();
    }

    public void parar() {
        ativo = false;
        try {
            if (socketEscuta != null) {
                socketEscuta.close();
            }
        } catch (IOException ignorada) {
            // nada a fazer
        }
    }

    /**
     * Escolhe o agente da UCI, nesta ordem: API compatível com OpenAI (UCI_PROVIDER/UCI_API_URL:
     * Ollama, Groq...), depois Anthropic (ANTHROPIC_API_KEY) e, por fim, o modo local (offline).
     */
    static AgenteIA escolherAgente() {
        return escolherAgente(System::getenv);
    }

    static AgenteIA escolherAgente(Function<String, String> ambiente) {
        try {
            Optional<AgenteCompativel> compativel = AgenteCompativel.deAmbiente(ambiente);
            if (compativel.isPresent()) {
                return compativel.get();
            }
        } catch (IllegalArgumentException e) {
            LOG.warning("Configuração da UCI inválida (" + e.getMessage() + "). Usando o modo local.");
            return new AgenteLocal();
        }
        return AgenteAnthropic.deAmbiente(ambiente).<AgenteIA>map(a -> a).orElseGet(AgenteLocal::new);
    }

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : PORTA_PADRAO;

        java.nio.file.Path arquivoLog = java.nio.file.Path.of("logs", "chat-comandos.log");
        try {
            LogComandos.gravarEmArquivo(arquivoLog);
            LOG.info("Log de comandos gravado em " + arquivoLog.toAbsolutePath());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Não foi possível criar o log de comandos em arquivo (" + e.getMessage()
                    + "); os registros aparecem só no console.");
        }

        ConexaoFactory conexoes = ConexaoFactory.padrao();
        JdbcMensagemRepository mensagens = new JdbcMensagemRepository(conexoes);
        mensagens.criarEsquema();

        AgenteIA agente = escolherAgente(ConfiguracaoUci.combinar(
                System::getenv, java.nio.file.Path.of(ConfiguracaoUci.ARQUIVO_PADRAO), LOG));
        LOG.info("UCI usando: " + agente.descricao());
        ConfiguracaoUci.testarEmSegundoPlano(agente, LOG);
        ServicoUci uci = new ServicoUci(agente);

        SalaChat sala = new SalaChat(mensagens, new JdbcGrupoRepository(conexoes), uci, SalaChat.HISTORICO_PADRAO);
        sala.usarModerador(ModeradorPorLista.padrao());
        new Servidor(porta, sala).iniciar();
    }
}
