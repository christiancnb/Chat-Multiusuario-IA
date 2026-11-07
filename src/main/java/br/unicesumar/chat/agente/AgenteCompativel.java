package br.unicesumar.chat.agente;

import br.unicesumar.chat.agente.Turno.Papel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Agente para qualquer serviço que fale o formato "chat completions" da OpenAI
 * ({@code POST <base>/chat/completions}), que virou um padrão de fato. Isso permite usar
 * opções <b>gratuitas e leves</b> sem trocar o código:
 * <ul>
 *   <li><b>Ollama</b>: roda no próprio computador, sem conta, sem chave e sem custo;</li>
 *   <li>serviços com camada gratuita (Groq, OpenRouter, Gemini...), com uma chave gratuita.</li>
 * </ul>
 *
 * <p>Variáveis de ambiente (veja {@link #deAmbiente()}):</p>
 * <ul>
 *   <li>{@code UCI_PROVIDER}: atalho {@code ollama} ou {@code groq} (preenche URL e modelo);</li>
 *   <li>{@code UCI_API_URL}: endereço base, ex.: {@code http://localhost:11434/v1};</li>
 *   <li>{@code UCI_API_KEY}: chave (opcional: o Ollama não usa). Só no ambiente, nunca no código;</li>
 *   <li>{@code UCI_MODEL}: nome do modelo (tem padrão nos atalhos).</li>
 * </ul>
 */
public final class AgenteCompativel implements AgenteIA {

    static final String URL_OLLAMA = "http://localhost:11434/v1";
    static final String MODELO_OLLAMA = "llama3.2:1b"; // ~1,3 GB: roda em PC comum, sem placa de vídeo
    static final String URL_GROQ = "https://api.groq.com/openai/v1";
    static final String MODELO_GROQ = "llama-3.1-8b-instant";
    static final int MAX_TOKENS = 1500; // folga: modelos que "pensam" (gpt-oss, qwen) gastam parte disso raciocinando

    private final HttpClient http;
    private final URI endpoint;
    private final String chaveApi; // pode ser vazia
    private final String modelo;
    private final String nome;
    private final Duration limiteDeTempo;

    public AgenteCompativel(String nome, String urlBase, String chaveApi, String modelo) {
        // modelos locais pequenos podem demorar na primeira resposta (carregam na memória)
        this(nome, urlBase, chaveApi, modelo, Duration.ofSeconds(90));
    }

    AgenteCompativel(String nome, String urlBase, String chaveApi, String modelo, Duration limiteDeTempo) {
        if (urlBase == null || urlBase.isBlank()) {
            throw new IllegalArgumentException("O endereço da API é obrigatório.");
        }
        if (modelo == null || modelo.isBlank()) {
            throw new IllegalArgumentException("O nome do modelo é obrigatório (defina UCI_MODEL).");
        }
        String base = urlBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.nome = nome == null || nome.isBlank() ? "API compatível" : nome.trim();
        this.endpoint = URI.create(base + "/chat/completions");
        this.chaveApi = chaveApi == null ? "" : chaveApi.trim();
        this.modelo = modelo.trim();
        this.limiteDeTempo = limiteDeTempo;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Cria o agente pelo ambiente do processo; vazio se nem UCI_PROVIDER nem UCI_API_URL foram definidos. */
    public static Optional<AgenteCompativel> deAmbiente() {
        return deAmbiente(System::getenv);
    }

    /** @throws IllegalArgumentException se a configuração estiver incompleta ou o atalho for desconhecido */
    public static Optional<AgenteCompativel> deAmbiente(Function<String, String> ambiente) {
        String provedor = vazioParaNulo(ambiente.apply("UCI_PROVIDER"));
        String url = vazioParaNulo(ambiente.apply("UCI_API_URL"));
        if (provedor == null && url == null) {
            return Optional.empty();
        }
        String modelo = vazioParaNulo(ambiente.apply("UCI_MODEL"));
        String nome = "API compatível";
        if (provedor != null) {
            switch (provedor.toLowerCase(Locale.ROOT)) {
                case "ollama" -> {
                    nome = "Ollama";
                    url = url != null ? url : URL_OLLAMA;
                    modelo = modelo != null ? modelo : MODELO_OLLAMA;
                }
                case "groq" -> {
                    nome = "Groq";
                    url = url != null ? url : URL_GROQ;
                    modelo = modelo != null ? modelo : MODELO_GROQ;
                }
                default -> throw new IllegalArgumentException(
                        "UCI_PROVIDER desconhecido: '" + provedor + "'. Use ollama ou groq, ou defina UCI_API_URL.");
            }
        }
        return Optional.of(new AgenteCompativel(nome, url, ambiente.apply("UCI_API_KEY"), modelo));
    }

    @Override
    public String descricao() {
        return nome + " (modelo " + modelo + ")";
    }

    @Override
    public String responder(List<Turno> conversa) throws AgenteException {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(endpoint)
                .timeout(limiteDeTempo)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(montarCorpo(conversa), StandardCharsets.UTF_8));
        if (!chaveApi.isEmpty()) {
            pedido.header("authorization", "Bearer " + chaveApi);
        }

        HttpResponse<String> resposta;
        try {
            resposta = http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgenteException("A resposta da UCI foi interrompida.", "Thread interrompida.", e);
        } catch (IOException e) {
            throw new AgenteException("Não consegui falar com o serviço de IA agora.",
                    "Falha de rede/tempo esgotado (" + nome + " em " + endpoint + "): " + e, e);
        }

        int status = resposta.statusCode();
        if (status != 200) {
            throw new AgenteException(AgenteAnthropic.mensagemParaStatus(status),
                    "HTTP " + status + ": " + AgenteAnthropic.abreviar(resposta.body()));
        }
        return extrairTexto(resposta.body());
    }

    /**
     * Pergunta ao serviço quais modelos ele oferece ({@code GET <base>/models}), para ajudar quando o
     * nome configurado em UCI_MODEL não existe. Devolve lista vazia se o serviço não suportar.
     */
    public List<String> listarModelos() {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(endpoint.toString().replaceFirst("/chat/completions$", "/models")))
                .timeout(Duration.ofSeconds(15)).GET();
        if (!chaveApi.isEmpty()) {
            pedido.header("authorization", "Bearer " + chaveApi);
        }
        try {
            HttpResponse<String> r = http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return r.statusCode() == 200 ? extrairModelos(r.body()) : List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** Lê {@code data[].id}. */
    static List<String> extrairModelos(String corpoJson) {
        List<String> ids = new java.util.ArrayList<>();
        try {
            if (Json.interpretar(corpoJson) instanceof Map<?, ?> m && m.get("data") instanceof List<?> lista) {
                for (Object o : lista) {
                    if (o instanceof Map<?, ?> item && item.get("id") instanceof String id) {
                        ids.add(id);
                    }
                }
            }
        } catch (IllegalArgumentException ignorada) {
            // resposta ilegível: sem sugestões
        }
        java.util.Collections.sort(ids);
        return ids;
    }

    // ---- montagem e leitura (package-private para teste) ----

    String montarCorpo(List<Turno> conversa) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":").append(Json.texto(modelo))
                .append(",\"max_tokens\":").append(MAX_TOKENS)
                .append(",\"messages\":[{\"role\":\"system\",\"content\":")
                .append(Json.texto(AgenteAnthropic.PERSONA)).append('}');
        for (Turno t : AgenteAnthropic.alternar(conversa)) {
            sb.append(",{\"role\":").append(Json.texto(t.papel() == Papel.USUARIO ? "user" : "assistant"))
                    .append(",\"content\":").append(Json.texto(t.texto())).append('}');
        }
        return sb.append("]}").toString();
    }

    /** Lê {@code choices[0].message.content}. */
    static String extrairTexto(String corpoJson) throws AgenteException {
        Object raiz;
        try {
            raiz = Json.interpretar(corpoJson);
        } catch (IllegalArgumentException e) {
            throw new AgenteException("A UCI recebeu uma resposta ilegível do serviço de IA.", e.getMessage(), e);
        }
        if (raiz instanceof Map<?, ?> mapa && mapa.get("choices") instanceof List<?> escolhas
                && !escolhas.isEmpty() && escolhas.get(0) instanceof Map<?, ?> escolha
                && escolha.get("message") instanceof Map<?, ?> mensagem
                && mensagem.get("content") instanceof String texto && !texto.isBlank()) {
            return texto;
        }
        throw new AgenteException("A UCI não conseguiu formular uma resposta desta vez.",
                "Resposta sem texto: " + AgenteAnthropic.abreviar(corpoJson));
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
