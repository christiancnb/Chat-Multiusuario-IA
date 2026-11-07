package br.unicesumar.chat.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Entidade de domínio: uma mensagem do chat (tipo, destino, remetente, conteúdo e horário).
 *
 * <p>Existem três {@link Tipo tipos}: {@code GERAL} (sala comum, sem destino), {@code GRUPO}
 * (destino = nome do grupo) e {@code PRIVADA} (destino = nome do destinatário).</p>
 *
 * <p>É imutável, o que a torna segura para ser compartilhada entre threads sem
 * sincronização. Regras de negócio validadas na construção:</p>
 * <ul>
 *   <li>remetente e conteúdo não podem ser vazios;</li>
 *   <li>conteúdo com no máximo {@value #TAMANHO_MAXIMO} caracteres;</li>
 *   <li>conteúdo não pode ter quebra de linha (cada mensagem é uma linha do protocolo);</li>
 *   <li>grupo e privada exigem destino; a geral não tem destino.</li>
 * </ul>
 */
public final class Mensagem {

    /** Para onde a mensagem foi enviada. */
    public enum Tipo { GERAL, GRUPO, PRIVADA }

    public static final int TAMANHO_MAXIMO = 500;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Long id;
    private final Tipo tipo;
    private final String destino;
    private final String remetente;
    private final String conteudo;
    private final LocalDateTime horario;

    /** Cria uma mensagem GERAL nova, com o horário atual e ainda sem id (não persistida). */
    public Mensagem(String remetente, String conteudo) {
        this(null, remetente, conteudo, LocalDateTime.now());
    }

    /** Cria uma mensagem GERAL nova com horário informado (útil em testes). */
    public Mensagem(String remetente, String conteudo, LocalDateTime horario) {
        this(null, remetente, conteudo, horario);
    }

    /** Construtor para mensagens GERAIS, usado também para reconstruir mensagens lidas do banco. */
    public Mensagem(Long id, String remetente, String conteudo, LocalDateTime horario) {
        this(id, Tipo.GERAL, null, remetente, conteudo, horario);
    }

    /** Construtor completo. */
    public Mensagem(Long id, Tipo tipo, String destino, String remetente, String conteudo, LocalDateTime horario) {
        Objects.requireNonNull(tipo, "O tipo da mensagem é obrigatório.");
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalArgumentException("O remetente da mensagem é obrigatório.");
        }
        if (conteudo == null || conteudo.isBlank()) {
            throw new IllegalArgumentException("A mensagem não pode ser vazia.");
        }
        String texto = conteudo.trim();
        if (texto.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "A mensagem excede o limite de " + TAMANHO_MAXIMO + " caracteres.");
        }
        if (texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("A mensagem não pode conter quebras de linha.");
        }
        Objects.requireNonNull(horario, "O horário da mensagem é obrigatório.");
        if (tipo != Tipo.GERAL && (destino == null || destino.isBlank())) {
            throw new IllegalArgumentException("Mensagens de grupo ou privadas exigem um destino.");
        }

        this.id = id;
        this.tipo = tipo;
        this.destino = tipo == Tipo.GERAL ? null : destino.trim();
        this.remetente = remetente.trim();
        this.conteudo = texto;
        // Milissegundos bastam e evitam diferenças de precisão entre Java e banco.
        this.horario = horario.truncatedTo(ChronoUnit.MILLIS);
    }

    /** Mensagem nova para um grupo (o nome do grupo deve estar normalizado, em minúsculas). */
    public static Mensagem grupo(String grupo, String remetente, String conteudo) {
        return grupo(grupo, remetente, conteudo, LocalDateTime.now());
    }

    public static Mensagem grupo(String grupo, String remetente, String conteudo, LocalDateTime horario) {
        return new Mensagem(null, Tipo.GRUPO, grupo, remetente, conteudo, horario);
    }

    /** Mensagem privada nova de {@code remetente} para {@code destinatario}. */
    public static Mensagem privada(String destinatario, String remetente, String conteudo) {
        return privada(destinatario, remetente, conteudo, LocalDateTime.now());
    }

    public static Mensagem privada(String destinatario, String remetente, String conteudo, LocalDateTime horario) {
        return new Mensagem(null, Tipo.PRIVADA, destinatario, remetente, conteudo, horario);
    }

    /** Devolve uma cópia desta mensagem com o id gerado pelo banco. */
    public Mensagem comId(long novoId) {
        return new Mensagem(novoId, tipo, destino, remetente, conteudo, horario);
    }

    /**
     * Texto exibido aos clientes. O formato depende do tipo e é interpretado por
     * {@code br.unicesumar.chat.protocolo.Interpretador}:
     * <pre>
     * [21:03:15] ana: olá, pessoal!                    (geral)
     * [21:03:15] #estudo ana: olá, grupo!              (grupo)
     * [21:03:15] ana -&gt; bia (privado): oi, bia        (privada)
     * </pre>
     */
    public String formatar() {
        String hora = "[" + FORMATO_HORA.format(horario) + "] ";
        return switch (tipo) {
            case GERAL -> hora + remetente + ": " + conteudo;
            case GRUPO -> hora + "#" + destino + " " + remetente + ": " + conteudo;
            case PRIVADA -> hora + remetente + " -> " + destino + " (privado): " + conteudo;
        };
    }

    public Long getId() {
        return id;
    }

    public Tipo getTipo() {
        return tipo;
    }

    /** Nome do grupo ou do destinatário; {@code null} nas mensagens gerais. */
    public String getDestino() {
        return destino;
    }

    public String getRemetente() {
        return remetente;
    }

    public String getConteudo() {
        return conteudo;
    }

    public LocalDateTime getHorario() {
        return horario;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mensagem outra)) return false;
        return Objects.equals(id, outra.id)
                && tipo == outra.tipo
                && Objects.equals(destino, outra.destino)
                && remetente.equals(outra.remetente)
                && conteudo.equals(outra.conteudo)
                && horario.equals(outra.horario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tipo, destino, remetente, conteudo, horario);
    }

    @Override
    public String toString() {
        return "Mensagem{id=" + id + ", " + formatar() + "}";
    }
}
