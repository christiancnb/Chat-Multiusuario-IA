package br.unicesumar.chat.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Cliente de mentira: guarda em memória as linhas recebidas, sem usar rede. */
public class DestinatarioFalso implements Destinatario {

    private final String nome;
    private final List<String> recebidas = new CopyOnWriteArrayList<>();
    private volatile boolean aceitando = true;
    private volatile boolean controle;

    public DestinatarioFalso(String nome) {
        this.nome = nome;
    }

    @Override
    public String getNome() {
        return nome;
    }

    @Override
    public boolean enviar(String linha) {
        if (!aceitando) {
            return false;
        }
        recebidas.add(linha);
        return true;
    }

    @Override
    public boolean aceitaControle() {
        return controle;
    }

    @Override
    public void ativarControle() {
        controle = true;
    }

    public void simularQueda() {
        aceitando = false;
    }

    public List<String> recebidas() {
        return recebidas;
    }

    /** Apenas as linhas de conversa (ignora avisos "*", cabeçalhos "---", erros "!" e controle "@@"). */
    public List<String> conversa() {
        return recebidas.stream()
                .filter(l -> !l.startsWith("*") && !l.startsWith("---") && !l.startsWith("!") && !l.startsWith("@@"))
                .toList();
    }

    /** Só as mensagens de chat, sem respostas de comandos como "Conectados: ...". */
    public List<String> mensagens() {
        return conversa().stream().filter(l -> l.startsWith("[")).toList();
    }

    /** Linhas de controle (lista de online e de grupos). */
    public List<String> controle() {
        return recebidas.stream().filter(l -> l.startsWith("@@")).toList();
    }

    /** Última linha de controle que começa com o prefixo, ou {@code null}. */
    public String ultimoControle(String prefixo) {
        String ultimo = null;
        for (String l : recebidas) {
            if (l.startsWith(prefixo)) {
                ultimo = l;
            }
        }
        return ultimo;
    }

    public boolean recebeuContendo(String trecho) {
        return recebidas.stream().anyMatch(l -> l.contains(trecho));
    }
}
