package br.unicesumar.chat.protocolo;

import java.util.List;

/**
 * O que uma linha enviada pelo servidor significa, já decomposto. É o resultado de
 * {@link Interpretador#interpretar(String)} e permite ao cliente gráfico rotear cada
 * linha para a conversa certa sem espalhar expressões regulares pela interface.
 */
public sealed interface Evento {

    record MensagemGeral(String hora, String remetente, String texto) implements Evento { }

    record MensagemGrupo(String hora, String grupo, String remetente, String texto) implements Evento { }

    record MensagemPrivada(String hora, String de, String para, String texto) implements Evento { }

    record AvisoGeral(String texto) implements Evento { }

    record AvisoGrupo(String grupo, String texto) implements Evento { }

    record CabecalhoGeral(String texto) implements Evento { }

    record CabecalhoGrupo(String grupo, String texto) implements Evento { }

    record CabecalhoPrivado(String com, String texto) implements Evento { }

    /** Erro de uma ação do usuário (ex.: "usuário não está online"). */
    record Erro(String texto) implements Evento { }

    /** Lista de quem está online (linha de controle). */
    record Usuarios(List<String> nomes) implements Evento { }

    /** Grupos do usuário e todos os grupos existentes (linha de controle). */
    record Grupos(List<String> meus, List<String> todos) implements Evento { }

    /** Status de quem não está disponível (usuário em minúsculas -> rótulo); os demais estão disponíveis. */
    record Status(java.util.Map<String, String> porUsuario) implements Evento { }

    /** {@code quem} está digitando em {@code conversa} ("geral", "#grupo" ou o nome do próprio {@code quem}). */
    record Digitando(String quem, String conversa) implements Evento { }

    /** Qualquer outra linha (respostas de comandos, ajuda etc.). */
    record Outra(String texto) implements Evento { }
}
