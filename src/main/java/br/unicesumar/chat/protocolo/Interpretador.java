package br.unicesumar.chat.protocolo;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte uma linha recebida do servidor em um {@link Evento}. É lógica pura (sem rede nem
 * Swing), por isso é facilmente testável.
 *
 * <p>Não há ambiguidade entre os formatos: o que vem antes dos dois-pontos é sempre um nome
 * (sem espaços e sem ':'), então o <i>texto</i> digitado pelo usuário nunca consegue se passar
 * por aviso, erro ou linha de controle.</p>
 */
public final class Interpretador {

    private static final String HORA = "\\[(\\d{2}:\\d{2}:\\d{2})] ";
    private static final Pattern PRIVADA = Pattern.compile("^" + HORA + "([^\\s:]+) -> ([^\\s:]+) \\(privado\\): (.*)$");
    private static final Pattern GRUPO = Pattern.compile("^" + HORA + "#([^\\s:]+) ([^\\s:]+): (.*)$");
    private static final Pattern GERAL = Pattern.compile("^" + HORA + "([^\\s:]+): (.*)$");
    private static final Pattern AVISO_GRUPO = Pattern.compile("^\\* #([^\\s:]+): (.*)$");
    private static final Pattern CABECALHO_GRUPO = Pattern.compile("^--- #([^\\s:]+): (.*) ---$");
    private static final Pattern CABECALHO_PRIVADO = Pattern.compile("^--- privado com ([^\\s:]+): (.*) ---$");

    private Interpretador() {
    }

    public static Evento interpretar(String linha) {
        if (linha == null) {
            return new Evento.Outra("");
        }
        if (linha.startsWith(Protocolo.PREFIXO_USUARIOS)) {
            return new Evento.Usuarios(lista(linha.substring(Protocolo.PREFIXO_USUARIOS.length())));
        }
        if (linha.startsWith(Protocolo.PREFIXO_GRUPOS)) {
            String[] partes = linha.substring(Protocolo.PREFIXO_GRUPOS.length()).split("\\|", -1);
            return new Evento.Grupos(lista(partes[0]), partes.length > 1 ? lista(partes[1]) : List.of());
        }
        if (linha.startsWith(Protocolo.PREFIXO_STATUS)) {
            java.util.Map<String, String> mapa = new java.util.LinkedHashMap<>();
            for (String par : lista(linha.substring(Protocolo.PREFIXO_STATUS.length()))) {
                int igual = par.indexOf('=');
                if (igual > 0) {
                    mapa.put(par.substring(0, igual).toLowerCase(java.util.Locale.ROOT), par.substring(igual + 1));
                }
            }
            return new Evento.Status(mapa);
        }
        if (linha.startsWith(Protocolo.PREFIXO_DIGITANDO)) {
            String[] partes = linha.substring(Protocolo.PREFIXO_DIGITANDO.length()).split("\\|", 2);
            return partes.length == 2 ? new Evento.Digitando(partes[0], partes[1]) : new Evento.Outra(linha);
        }
        if (linha.startsWith("! ")) {
            return new Evento.Erro(linha.substring(2));
        }
        if (linha.startsWith("* ")) {
            Matcher m = AVISO_GRUPO.matcher(linha);
            return m.matches()
                    ? new Evento.AvisoGrupo(m.group(1), m.group(2))
                    : new Evento.AvisoGeral(linha.substring(2));
        }
        if (linha.startsWith("---")) {
            Matcher g = CABECALHO_GRUPO.matcher(linha);
            if (g.matches()) {
                return new Evento.CabecalhoGrupo(g.group(1), g.group(2));
            }
            Matcher p = CABECALHO_PRIVADO.matcher(linha);
            if (p.matches()) {
                return new Evento.CabecalhoPrivado(p.group(1), p.group(2));
            }
            return new Evento.CabecalhoGeral(linha.replace("---", "").trim());
        }
        Matcher m = PRIVADA.matcher(linha);
        if (m.matches()) {
            return new Evento.MensagemPrivada(m.group(1), m.group(2), m.group(3), m.group(4));
        }
        m = GRUPO.matcher(linha);
        if (m.matches()) {
            return new Evento.MensagemGrupo(m.group(1), m.group(2), m.group(3), m.group(4));
        }
        m = GERAL.matcher(linha);
        if (m.matches()) {
            return new Evento.MensagemGeral(m.group(1), m.group(2), m.group(3));
        }
        return new Evento.Outra(linha);
    }

    private static List<String> lista(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
