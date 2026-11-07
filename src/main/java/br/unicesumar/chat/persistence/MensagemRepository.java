package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;

import java.util.List;

/**
 * Contrato de persistência de mensagens. O restante do sistema depende desta
 * interface, nunca de SQL ou JDBC, o que permite testar a lógica de negócio
 * com uma implementação em memória.
 */
public interface MensagemRepository {

    /** Grava a mensagem (de qualquer tipo) e devolve uma cópia com o id gerado. */
    Mensagem salvar(Mensagem mensagem);

    /**
     * Devolve as últimas {@code n} mensagens GERAIS em ordem cronológica
     * (da mais antiga para a mais recente).
     *
     * @throws IllegalArgumentException se {@code n <= 0}
     */
    List<Mensagem> buscarUltimas(int n);

    /**
     * Últimas {@code n} mensagens do grupo (nome normalizado), em ordem cronológica.
     *
     * @throws IllegalArgumentException se {@code n <= 0}
     */
    List<Mensagem> buscarUltimasDoGrupo(String grupo, int n);

    /**
     * Últimas {@code n} mensagens privadas trocadas entre os dois usuários (nos dois sentidos,
     * sem diferenciar maiúsculas), em ordem cronológica.
     *
     * @throws IllegalArgumentException se {@code n <= 0}
     */
    List<Mensagem> buscarUltimasPrivadas(String usuarioA, String usuarioB, int n);

    /**
     * Mensagens de QUALQUER tipo cujo conteúdo contém o termo (sem diferenciar maiúsculas), as
     * {@code limite} mais recentes, em ordem cronológica. Quem chama decide o que cada usuário pode ver.
     *
     * @throws IllegalArgumentException se {@code limite <= 0} ou o termo estiver em branco
     */
    List<Mensagem> buscarPorTexto(String termo, int limite);
}
