package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.PersistenciaException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Implementação em memória do repositório, para testar a sala sem banco. */
public class RepositorioEmMemoria implements MensagemRepository {

    private final List<Mensagem> mensagens = new ArrayList<>();
    private volatile boolean falhar;

    public void falharSempre() {
        falhar = true;
    }

    @Override
    public synchronized Mensagem salvar(Mensagem mensagem) {
        verificar();
        Mensagem salva = mensagem.comId(mensagens.size() + 1L);
        mensagens.add(salva);
        return salva;
    }

    @Override
    public synchronized List<Mensagem> buscarUltimas(int n) {
        return ultimas(m -> m.getTipo() == Mensagem.Tipo.GERAL, n);
    }

    @Override
    public synchronized List<Mensagem> buscarUltimasDoGrupo(String grupo, int n) {
        return ultimas(m -> m.getTipo() == Mensagem.Tipo.GRUPO && m.getDestino().equalsIgnoreCase(grupo), n);
    }

    @Override
    public synchronized List<Mensagem> buscarUltimasPrivadas(String usuarioA, String usuarioB, int n) {
        String a = Usuario.chave(usuarioA);
        String b = Usuario.chave(usuarioB);
        return ultimas(m -> m.getTipo() == Mensagem.Tipo.PRIVADA && (
                (Usuario.chave(m.getRemetente()).equals(a) && Usuario.chave(m.getDestino()).equals(b))
                        || (Usuario.chave(m.getRemetente()).equals(b) && Usuario.chave(m.getDestino()).equals(a))), n);
    }

    @Override
    public synchronized List<Mensagem> buscarPorTexto(String termo, int limite) {
        if (termo == null || termo.isBlank()) {
            throw new IllegalArgumentException("Informe o texto a buscar.");
        }
        String t = termo.trim().toLowerCase(java.util.Locale.ROOT);
        return ultimas(m -> m.getConteudo().toLowerCase(java.util.Locale.ROOT).contains(t), limite);
    }

    public synchronized List<Mensagem> todas() {
        return new ArrayList<>(mensagens);
    }

    private List<Mensagem> ultimas(Predicate<Mensagem> filtro, int n) {
        verificar();
        if (n <= 0) {
            throw new IllegalArgumentException("n deve ser positivo");
        }
        List<Mensagem> escolhidas = mensagens.stream().filter(filtro).toList();
        return new ArrayList<>(escolhidas.subList(Math.max(0, escolhidas.size() - n), escolhidas.size()));
    }

    private void verificar() {
        if (falhar) {
            throw new PersistenciaException("banco fora do ar (simulado)");
        }
    }
}
