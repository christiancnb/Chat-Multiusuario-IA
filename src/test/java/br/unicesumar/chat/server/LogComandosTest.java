package br.unicesumar.chat.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O log de comandos diz quem digitou, o que digitou e por que falhou; e erro inesperado não derruba o usuário. */
class LogComandosTest {

    /** Guarda os registros em memória para o teste conferir. */
    private static final class Captura extends Handler {
        final List<LogRecord> registros = new ArrayList<>();
        private final LogComandos.FormatoLinha formato = new LogComandos.FormatoLinha();

        @Override public void publish(LogRecord r) { registros.add(r); }
        @Override public void flush() { }
        @Override public void close() { }

        String texto(int i) { return formato.formatMessage(registros.get(i)); }
    }

    private Captura captura;
    private SalaChat sala;
    private ComandosChat comandos;
    private DestinatarioFalso ana;

    @BeforeEach
    void preparar() {
        Logger logger = Logger.getLogger("teste.comandos." + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        captura = new Captura();
        logger.addHandler(captura);

        sala = new SalaChat(new RepositorioEmMemoria(), 20);
        comandos = new ComandosChat(sala, new LogComandos(logger));
        ana = new DestinatarioFalso("ana");
        sala.entrar(ana);
        ana.recebidas().clear();
    }

    @Test
    void comandoValidoEhRegistradoComoExecutado() {
        comandos.executar(ana, "/usuarios");

        assertEquals(1, captura.registros.size());
        assertEquals(Level.INFO, captura.registros.get(0).getLevel());
        assertEquals("[ana] \"/usuarios\" -> ok", captura.texto(0));
    }

    @Test
    void textoComumBemSucedidoNaoEhRegistrado() {
        comandos.executar(ana, "oi pessoal");

        assertTrue(captura.registros.isEmpty(), "o conteúdo das conversas não deve ir para o log");
    }

    @Test
    void comandoDesconhecidoInformaOMotivoAoUsuarioENoLog() {
        comandos.executar(ana, "/dancar agora");

        assertEquals("! Comando desconhecido: /dancar. Digite /ajuda.", ana.recebidas().get(0));
        assertEquals(Level.WARNING, captura.registros.get(0).getLevel());
        assertEquals("[ana] \"/dancar agora\" -> Comando desconhecido: /dancar. Digite /ajuda.", captura.texto(0));
    }

    @Test
    void usoIncorretoRegistraAAssinaturaEsperada() {
        comandos.executar(ana, "/msg bia");

        assertEquals("! Uso: /msg <usuario> <texto>", ana.recebidas().get(0));
        assertTrue(captura.texto(0).endsWith("-> Uso: /msg <usuario> <texto>"));
    }

    @Test
    void erroDeRegraDeNegocioEhRegistradoComOMotivo() {
        comandos.executar(ana, "/grupo entrar fantasma");
        comandos.executar(ana, "/msg zeca oi");

        assertTrue(captura.texto(0).contains("O grupo #fantasma não existe"));
        assertTrue(captura.texto(1).contains("O usuário 'zeca' não está online."));
        assertEquals(2, ana.recebidas().size());
    }

    @Test
    void mensagemComumInvalidaTambemEhRegistradaComTrechoCurto() {
        comandos.executar(ana, "x".repeat(501));

        assertEquals(Level.WARNING, captura.registros.get(0).getLevel());
        assertTrue(captura.texto(0).contains("excede o limite de 500"));
        assertTrue(captura.texto(0).length() < 200, "o log não deve repetir a mensagem inteira");
    }

    @Test
    void falhaInesperadaViraErroNoLogEAConexaoContinua() {
        List<String> recebidas = new ArrayList<>();
        Destinatario fragil = new Destinatario() {
            private boolean falhou;

            @Override public String getNome() { return "zed"; }

            @Override public boolean enviar(String linha) {
                if (!falhou) {
                    falhou = true;
                    throw new IllegalStateException("explodiu");
                }
                recebidas.add(linha);
                return true;
            }
        };

        ComandosChat.Resultado r = comandos.executar(fragil, "/usuarios");

        assertEquals(ComandosChat.Resultado.CONTINUAR, r);
        assertEquals(Level.SEVERE, captura.registros.get(0).getLevel());
        assertNotNull(captura.registros.get(0).getThrown());
        assertTrue(recebidas.get(0).startsWith("! Erro interno ao executar o comando (IllegalStateException)"));
    }

    @Test
    void formatoDeArquivoTemUmaLinhaComDataNivelEMotivo() {
        comandos.executar(ana, "/dancar");

        String linha = new LogComandos.FormatoLinha().format(captura.registros.get(0));

        assertTrue(linha.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} AVISO \\[ana\\] \"/dancar\" -> .*\\R"), linha);
    }
}
