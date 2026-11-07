package br.unicesumar.chat.server;

import br.unicesumar.chat.agente.Personalidade;
import br.unicesumar.chat.model.StatusUsuario;
import br.unicesumar.chat.protocolo.Protocolo;

import java.util.List;
import java.util.Locale;

/**
 * Interpreta o que um cliente digita: texto comum vira mensagem na sala geral e linhas que
 * começam com {@code /} são comandos. Fica separada do {@link ClienteHandler} (que cuida só da
 * rede), então pode ser testada sem sockets, apenas com um {@link Destinatario} de mentira.
 *
 * <pre>
 * /msg &lt;usuario&gt; &lt;texto&gt;          mensagem privada (/p e /privado também servem)
 * /g &lt;grupo&gt; &lt;texto&gt;              mensagem para um grupo
 * /grupo criar|entrar|sair|membros &lt;nome&gt;
 * /grupos                          lista os grupos
 * /historico &lt;usuario|#grupo&gt;      mostra mensagens anteriores
 * /usuarios, /ajuda, /modo gui, /sair
 * </pre>
 */
public final class ComandosChat {

    /** O que o chamador deve fazer depois do comando. */
    public enum Resultado { CONTINUAR, SAIR }

    private final SalaChat sala;
    private final LogComandos log;

    public ComandosChat(SalaChat sala) {
        this(sala, new LogComandos());
    }

    public ComandosChat(SalaChat sala, LogComandos log) {
        this.sala = sala;
        this.log = log;
    }

    /**
     * Executa a linha digitada. Qualquer erro vira uma linha "! motivo" só para quem digitou e
     * um registro no {@link LogComandos}; nenhum erro derruba a conexão do usuário.
     */
    public Resultado executar(Destinatario usuario, String entrada) {
        String linha = entrada == null ? "" : entrada.trim();
        if (linha.isEmpty()) {
            return Resultado.CONTINUAR;
        }
        String nome = usuario.getNome();
        try {
            Resultado resultado = despachar(usuario, linha);
            if (linha.startsWith("/") && !silencioso(linha)) {
                log.executado(nome, linha);
            }
            return resultado;
        } catch (IllegalArgumentException | ChatException e) {
            usuario.enviar("! " + e.getMessage());
            log.recusado(nome, linha, e.getMessage());
        } catch (RuntimeException e) {
            log.falhou(nome, linha, e);
            usuario.enviar("! Erro interno ao executar o comando (" + e.getClass().getSimpleName()
                    + "). O problema foi registrado no log do servidor.");
        }
        return Resultado.CONTINUAR;
    }

    private Resultado despachar(Destinatario usuario, String linha) {
        if (!linha.startsWith("/")) {
            sala.publicar(usuario, linha);
            return Resultado.CONTINUAR;
        }
        String[] p = linha.split("\\s+", 3);
        switch (p[0].toLowerCase(Locale.ROOT)) {
            case "/sair" -> {
                usuario.enviar("Até logo!");
                return Resultado.SAIR;
            }
            case "/usuarios" -> usuario.enviar("Conectados: " + String.join(", ",
                    sala.listarUsuarios().stream().map(this::comStatus).toList()));
            case "/status" -> status(usuario, p);
            case "/exportar" -> throw new IllegalArgumentException(
                    "O /exportar funciona no cliente de terminal. Na janela, use o botão Exportar.");
            case "/buscar" -> sala.buscar(usuario, argumentos(linha));
            case "/uci" -> uci(usuario, p, linha);
            case Protocolo.COMANDO_DIGITANDO -> sala.avisarDigitando(usuario, p.length > 1 ? p[1] : "");
            case "/ajuda" -> ajuda(usuario);
            case "/modo" -> modo(usuario, p);
            case "/msg", "/p", "/privado" -> {
                exigirArgumentos(p, 3, "Uso: /msg <usuario> <texto>");
                sala.publicarPrivada(usuario, p[1], p[2]);
            }
            case "/g" -> {
                exigirArgumentos(p, 3, "Uso: /g <grupo> <texto>");
                sala.publicarNoGrupo(usuario, p[1], p[2]);
            }
            case "/grupos" -> listarGrupos(usuario);
            case "/grupo" -> grupo(usuario, p);
            case "/historico" -> {
                exigirArgumentos(p, 2, "Uso: /historico <usuario|#grupo>");
                if (p[1].startsWith("#")) {
                    sala.enviarHistoricoDoGrupo(usuario, p[1]);
                } else {
                    sala.enviarHistoricoPrivado(usuario, p[1]);
                }
            }
            default -> throw new IllegalArgumentException(
                    "Comando desconhecido: " + p[0] + ". Digite /ajuda.");
        }
        return Resultado.CONTINUAR;
    }

    private void grupo(Destinatario usuario, String[] p) {
        String uso = "Uso: /grupo criar|entrar|sair|membros <nome>";
        exigirArgumentos(p, 3, uso);
        String nome = p[2];
        switch (p[1].toLowerCase(Locale.ROOT)) {
            case "criar" -> sala.criarGrupo(usuario, nome);
            case "entrar" -> sala.entrarNoGrupo(usuario, nome);
            case "sair" -> sala.sairDoGrupo(usuario, nome);
            case "remover" -> {
                String[] alvo = nome.split("\\s+", 2);
                if (alvo.length < 2 || alvo[1].isBlank()) {
                    throw new IllegalArgumentException("Uso: /grupo remover <grupo> <usuario>");
                }
                sala.removerDoGrupo(usuario, alvo[0], alvo[1]);
            }
            case "membros" -> {
                List<String> membros = sala.membrosDoGrupo(nome);
                String grupo = nome.startsWith("#") ? nome.substring(1) : nome;
                usuario.enviar(Protocolo.avisoGrupo(grupo.toLowerCase(Locale.ROOT).trim(),
                        "Membros: " + String.join(", ", membros)));
            }
            default -> throw new IllegalArgumentException(uso);
        }
    }

    private void listarGrupos(Destinatario usuario) {
        List<String> meus = sala.nomesDosGruposDe(usuario.getNome());
        List<String> outros = sala.nomesDosGrupos().stream().filter(g -> !meus.contains(g)).toList();
        usuario.enviar("Seus grupos: " + (meus.isEmpty() ? "(nenhum)" : String.join(", ", meus)));
        usuario.enviar("Outros grupos: " + (outros.isEmpty() ? "(nenhum)" : String.join(", ", outros)));
    }

    /** Comandos "de bastidor" (ex.: aviso de digitação) não vão para o log: seriam centenas de linhas. */
    private static boolean silencioso(String linha) {
        return linha.regionMatches(true, 0, Protocolo.COMANDO_DIGITANDO, 0, Protocolo.COMANDO_DIGITANDO.length());
    }

    private String comStatus(String nome) {
        StatusUsuario s = sala.statusDe(nome);
        return s == StatusUsuario.DISPONIVEL ? nome : nome + " (" + s.rotulo() + ")";
    }

    private void status(Destinatario usuario, String[] p) {
        String uso = "Uso: /status disponivel|ocupado|ausente";
        if (p.length < 2) {
            usuario.enviar("Seu status: " + sala.statusDe(usuario.getNome()).rotulo() + ". " + uso);
            return;
        }
        StatusUsuario novo = StatusUsuario.de(p[1]).orElseThrow(() -> new IllegalArgumentException(uso));
        sala.definirStatus(usuario, novo);
        usuario.enviar("Status alterado para " + novo.rotulo() + ".");
    }

    private void uci(Destinatario usuario, String[] p, String linha) {
        String uso = "Uso: /uci modo [padrao|formal|descontraida|professora] | /uci resumir [usuario|#grupo] | "
                + "/uci traduzir <idioma> <texto> | /uci explicar <texto>";
        if (p.length < 2) {
            throw new IllegalArgumentException(uso);
        }
        String resto = p.length > 2 ? p[2].trim() : "";
        switch (p[1].toLowerCase(Locale.ROOT)) {
            case "modo" -> {
                if (resto.isEmpty()) {
                    usuario.enviar("Modo da UCI para você: " + sala.personalidadeDe(usuario.getNome()).nome()
                            + ". Opções: padrao, formal, descontraida, professora.");
                    return;
                }
                Personalidade nova = Personalidade.de(resto).orElseThrow(() -> new IllegalArgumentException(
                        "Modo desconhecido '" + resto + "'. Opções: padrao, formal, descontraida, professora."));
                sala.definirPersonalidade(usuario, nova);
                usuario.enviar("A UCI agora responde no modo " + nova.nome() + " para você.");
            }
            case "resumir" -> sala.pedirUtilidadeUci(usuario,
                    "Resuma em até 4 frases a conversa a seguir (cada mensagem separada por '|'): "
                            + sala.textoParaResumo(usuario, resto));
            case "traduzir" -> {
                String[] partes = resto.split("\\s+", 2);
                if (partes.length < 2 || partes[1].isBlank()) {
                    throw new IllegalArgumentException("Uso: /uci traduzir <idioma> <texto>");
                }
                sala.pedirUtilidadeUci(usuario, "Traduza para " + partes[0] + ", respondendo só com a tradução: " + partes[1]);
            }
            case "explicar" -> {
                if (resto.isEmpty()) {
                    throw new IllegalArgumentException("Uso: /uci explicar <texto>");
                }
                sala.pedirUtilidadeUci(usuario, "Explique de forma simples e curta: " + resto);
            }
            default -> throw new IllegalArgumentException(uso);
        }
    }

    /** Tudo que vem depois do nome do comando. */
    private static String argumentos(String linha) {
        int espaco = linha.indexOf(' ');
        return espaco < 0 ? "" : linha.substring(espaco + 1).trim();
    }

    private void modo(Destinatario usuario, String[] p) {
        if (p.length >= 2 && p[1].equalsIgnoreCase("gui")) {
            usuario.ativarControle();
            sala.enviarEstado(usuario);
        } else {
            throw new IllegalArgumentException("Uso: /modo gui");
        }
    }

    private static void ajuda(Destinatario usuario) {
        usuario.enviar("Comandos:");
        usuario.enviar("  /msg <usuario> <texto>      mensagem privada (use UCI para falar com a assistente)");
        usuario.enviar("  /g <grupo> <texto>          mensagem para um grupo");
        usuario.enviar("  /grupo criar <nome>         cria um grupo (você entra nele)");
        usuario.enviar("  /grupo entrar <nome>        entra em um grupo existente");
        usuario.enviar("  /grupo sair <nome>          sai de um grupo");
        usuario.enviar("  /grupo membros <nome>       lista os membros de um grupo");
        usuario.enviar("  /grupo remover <grupo> <usuario>  (criador) remove um membro");
        usuario.enviar("  /grupos                     lista os grupos");
        usuario.enviar("  /historico <usuario|#grupo> mostra mensagens anteriores");
        usuario.enviar("  /usuarios                   quem está online (e o status de cada um)");
        usuario.enviar("  /status <disponivel|ocupado|ausente>  muda como você aparece");
        usuario.enviar("  /buscar <texto>             procura nas conversas que você pode ver");
        usuario.enviar("  /uci modo|resumir|traduzir|explicar   utilidades e estilo da UCI");
        usuario.enviar("  /exportar [arquivo]         salva a conversa em arquivo (terminal) ou abre o seletor (janela)");
        usuario.enviar("  /sair                       desconecta");
        usuario.enviar("Atenção: texto sem '/' vai para a sala geral. Para responder em privado ou em grupo, use /msg ou /g.");
        usuario.enviar("Dica: escreva @UCI em uma mensagem (geral ou de grupo) para chamar a assistente.");
    }

    private static void exigirArgumentos(String[] partes, int minimo, String uso) {
        if (partes.length < minimo || partes[minimo - 1].isBlank()) {
            throw new IllegalArgumentException(uso);
        }
    }
}
