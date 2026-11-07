package br.unicesumar.chat.protocolo;

import java.util.Collection;

/**
 * Constantes e montagem das linhas "especiais" do protocolo texto entre servidor e cliente.
 * É a fonte única da verdade: o servidor monta as linhas aqui e o {@link Interpretador}
 * (cliente) as lê, então os dois lados nunca divergem.
 *
 * <pre>
 * @@USUARIOS|ana,bia,UCI           controle: quem está online (só para clientes gráficos)
 * @@GRUPOS|estudo,time|estudo,time,xyz   controle: meus grupos | todos os grupos
 * @@STATUS|ana=ocupado,bia=ausente        controle: status de quem não está disponível
 * @@DIGITANDO|ana|#estudo                 controle: ana está digitando naquela conversa
 * * #estudo: bia entrou no grupo.  aviso de um grupo
 * --- #estudo: fim do histórico --- cabeçalho de histórico de um grupo
 * --- privado com bia: ... ---      cabeçalho de histórico privado
 * </pre>
 *
 * <p>As linhas de controle só são enviadas depois que o cliente envia {@link #MODO_GUI};
 * clientes de console nunca as veem.</p>
 */
public final class Protocolo {

    /** Comando que um cliente gráfico envia para passar a receber as linhas de controle. */
    public static final String MODO_GUI = "/modo gui";

    public static final String PREFIXO_USUARIOS = "@@USUARIOS|";
    public static final String PREFIXO_GRUPOS = "@@GRUPOS|";
    public static final String PREFIXO_STATUS = "@@STATUS|";
    public static final String PREFIXO_DIGITANDO = "@@DIGITANDO|";

    /** Comando (silencioso) que o cliente gráfico envia enquanto o usuário digita: {@code /digitando geral|#grupo|usuario}. */
    public static final String COMANDO_DIGITANDO = "/digitando";

    private Protocolo() {
    }

    public static String usuarios(Collection<String> nomes) {
        return PREFIXO_USUARIOS + String.join(",", nomes);
    }

    public static String grupos(Collection<String> meus, Collection<String> todos) {
        return PREFIXO_GRUPOS + String.join(",", meus) + "|" + String.join(",", todos);
    }

    /** Status dos usuários que NÃO estão disponíveis: {@code @@STATUS|ana=ocupado,bia=ausente}. */
    public static String status(java.util.Map<String, String> porUsuario) {
        return PREFIXO_STATUS + porUsuario.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining(","));
    }

    /** {@code conversa} é como o DESTINATÁRIO a enxerga: "geral", "#grupo" ou o nome de quem digita (privado). */
    public static String digitando(String quem, String conversa) {
        return PREFIXO_DIGITANDO + quem + "|" + conversa;
    }

    public static String avisoGrupo(String grupo, String texto) {
        return "* #" + grupo + ": " + texto;
    }

    public static String cabecalhoGrupo(String grupo, String texto) {
        return "--- #" + grupo + ": " + texto + " ---";
    }

    public static String cabecalhoPrivado(String com, String texto) {
        return "--- privado com " + com + ": " + texto + " ---";
    }
}
