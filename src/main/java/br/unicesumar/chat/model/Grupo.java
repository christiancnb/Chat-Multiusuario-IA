package br.unicesumar.chat.model;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Entidade de domínio: um grupo de conversa. Qualquer usuário pode criar um grupo,
 * entrar nele e sair; as mensagens do grupo só chegam aos membros.
 *
 * <p>Regra de negócio: o nome tem de 2 a 20 caracteres (letras, números, '_' ou '-'), sem
 * espaços, e é <b>normalizado para minúsculas</b> ("Estudo" e "estudo" são o mesmo grupo).
 * Os membros são guardados pela forma canônica do nome de usuário ({@link Usuario#chave}).</p>
 *
 * <p><b>Não é thread-safe</b>: quem o usa ({@code SalaChat}) deve acessá-lo sob o seu lock.</p>
 */
public final class Grupo {

    private static final Pattern NOME_VALIDO = Pattern.compile("[\\p{L}\\p{N}_-]{2,20}");

    private final String nome;
    private final String criador;
    private final Set<String> membros = new TreeSet<>();

    public Grupo(String nome, String criador) {
        this.nome = normalizarNome(nome);
        if (criador == null || criador.isBlank()) {
            throw new IllegalArgumentException("O criador do grupo é obrigatório.");
        }
        this.criador = criador.trim();
    }

    /** Valida e normaliza o nome (remove um '#' inicial opcional e converte para minúsculas). */
    public static String normalizarNome(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new IllegalArgumentException("O nome do grupo é obrigatório.");
        }
        String limpo = bruto.trim();
        if (limpo.startsWith("#")) {
            limpo = limpo.substring(1);
        }
        if (!NOME_VALIDO.matcher(limpo).matches()) {
            throw new IllegalArgumentException(
                    "Nome de grupo inválido: use de 2 a 20 caracteres (letras, números, '_' ou '-'), sem espaços.");
        }
        return Usuario.chave(limpo);
    }

    public String getNome() {
        return nome;
    }

    public String getCriador() {
        return criador;
    }

    public boolean contem(String usuario) {
        return membros.contains(Usuario.chave(usuario));
    }

    /** @return {@code true} se o usuário não era membro e passou a ser */
    public boolean adicionar(String usuario) {
        return membros.add(Usuario.chave(usuario));
    }

    /** @return {@code true} se o usuário era membro e foi removido */
    public boolean remover(String usuario) {
        return membros.remove(Usuario.chave(usuario));
    }

    /** Cópia imutável dos membros (nomes canônicos, em ordem alfabética). */
    public Set<String> getMembros() {
        return Collections.unmodifiableSet(new TreeSet<>(membros));
    }

    /** Cópia independente deste grupo (útil para repositórios em memória). */
    public Grupo copiar() {
        Grupo copia = new Grupo(nome, criador);
        copia.membros.addAll(membros);
        return copia;
    }

    @Override
    public String toString() {
        return "Grupo{#" + nome + ", criador=" + criador + ", membros=" + membros + "}";
    }
}
