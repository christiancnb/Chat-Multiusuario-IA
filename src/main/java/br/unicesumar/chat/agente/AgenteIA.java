package br.unicesumar.chat.agente;

import java.util.List;

/**
 * Contrato de um agente de conversa. A UCI depende apenas desta interface, então o
 * provedor de IA pode ser trocado (ou simulado nos testes) sem mexer no restante do chat.
 * Implementações: {@link AgenteAnthropic} (API real) e {@link AgenteLocal} (sem internet).
 *
 * <p>Implementações podem demorar (rede), por isso nunca são chamadas pela thread de um
 * cliente nem dentro do lock da sala: o {@link ServicoUci} as executa em threads próprias.</p>
 */
public interface AgenteIA {

    /**
     * Gera a próxima resposta do agente.
     *
     * @param conversa fala(s) até agora, em ordem; a última é sempre do usuário
     * @return a resposta, em texto simples
     * @throws AgenteException se não foi possível obter a resposta
     */
    String responder(List<Turno> conversa) throws AgenteException;

    /** Nome curto do provedor, para o log de inicialização (ex.: "Anthropic (claude-...)"). */
    default String descricao() {
        return getClass().getSimpleName();
    }
}
