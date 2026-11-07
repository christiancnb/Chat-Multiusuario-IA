package br.unicesumar.chat.agente;

import br.unicesumar.chat.agente.Turno.Papel;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Agente simples, por palavras-chave, que funciona sem internet nem chave de API. É o
 * agente usado quando {@code ANTHROPIC_API_KEY} não está definida, o que permite demonstrar
 * o chat (e rodar os testes) sem depender de serviço externo. Não "entende" nada: apenas
 * reconhece algumas perguntas comuns e responde com textos prontos.
 */
public final class AgenteLocal implements AgenteIA {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Clock relogio;

    public AgenteLocal() {
        this(Clock.systemDefaultZone());
    }

    public AgenteLocal(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    public String descricao() {
        return "modo local (sem IA; configure UCI_PROVIDER, por exemplo ollama, para respostas completas)";
    }

    @Override
    public String responder(List<Turno> conversa) throws AgenteException {
        String pergunta = Personalidade.semInstrucao(ultimaFalaDoUsuario(conversa));
        String t = normalizar(pergunta);
        LocalDateTime agora = LocalDateTime.now(relogio);

        if (contemPalavra(t, "oi", "ola", "bom dia", "boa tarde", "boa noite", "e ai")) {
            return "Olá! Eu sou a UCI, a assistente do chat. Digite 'ajuda' para ver o que sei fazer.";
        }
        if (contemPalavra(t, "ajuda", "comandos", "como funciona", "como uso")) {
            return "Comandos: /msg usuario texto (privado), /grupo criar nome, /grupo entrar nome, "
                    + "/g grupo texto, /grupos, /usuarios, /sair. Na janela, clique duas vezes em um usuário ou grupo. "
                    + "Para me chamar em um grupo ou no chat geral, escreva @UCI na mensagem.";
        }
        if (contemPalavra(t, "horas", "hora")) {
            return "Agora são " + HORA.format(agora) + ".";
        }
        if (contemPalavra(t, "data", "dia e hoje", "que dia")) {
            return "Hoje é " + DATA.format(agora) + ".";
        }
        if (contemPalavra(t, "quem e voce", "quem e vc", "seu nome", "quem voce e", "quem e a uci")) {
            return "Sou a UCI, a assistente virtual do Chat Multiusuário.";
        }
        if (contemPalavra(t, "obrigad", "valeu")) {
            return "De nada! Se precisar, é só chamar.";
        }
        return "No momento estou em modo local, sem acesso à IA, então só respondo o básico. "
                + "Digite 'ajuda' para ver o que sei fazer.";
    }

    private static String ultimaFalaDoUsuario(List<Turno> conversa) throws AgenteException {
        for (int k = conversa.size() - 1; k >= 0; k--) {
            if (conversa.get(k).papel() == Papel.USUARIO) {
                return conversa.get(k).texto();
            }
        }
        throw new AgenteException("Não entendi a pergunta.", "Conversa sem fala do usuário.");
    }

    /** Minúsculas, sem acentos e sem pontuação, para comparar palavras com folga. */
    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return " " + semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim() + " ";
    }

    /** Procura cada termo como palavra inteira ("oi" não casa com "boi"); "obrigad" casa por prefixo. */
    private static boolean contemPalavra(String textoNormalizado, String... termos) {
        for (String termo : termos) {
            if (termo.equals("obrigad")) {
                if (textoNormalizado.contains(" obrigad")) {
                    return true;
                }
            } else if (textoNormalizado.contains(" " + termo + " ")) {
                return true;
            }
        }
        return false;
    }
}
