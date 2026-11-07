package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcMensagemRepositoryTest {

    private JdbcMensagemRepository repositorio;

    @BeforeEach
    void criarBanco() {
        repositorio = BancoDeTeste.novoRepositorio();
    }

    @Test
    void salvarAtribuiIdEPreservaOsDados() {
        Mensagem original = new Mensagem("ana", "olá", LocalDateTime.of(2026, 10, 8, 20, 0, 1));
        Mensagem salva = repositorio.salvar(original);

        assertNotNull(salva.getId());
        assertEquals("ana", salva.getRemetente());
        assertEquals("olá", salva.getConteudo());
        assertEquals(original.getHorario(), salva.getHorario());
    }

    @Test
    void buscarUltimasDevolveEmOrdemCronologicaLimitadaAN() {
        for (int i = 1; i <= 5; i++) {
            repositorio.salvar(new Mensagem("ana", "msg " + i));
        }

        List<Mensagem> ultimas = repositorio.buscarUltimas(3);

        assertEquals(List.of("msg 3", "msg 4", "msg 5"),
                ultimas.stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void buscarUltimasRecuperaOHorarioGravado() {
        LocalDateTime horario = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        repositorio.salvar(new Mensagem("bia", "oi", horario));

        Mensagem lida = repositorio.buscarUltimas(1).get(0);

        assertEquals(horario, lida.getHorario());
        assertEquals("bia", lida.getRemetente());
    }

    @Test
    void historicoVazioDevolveListaVazia() {
        assertTrue(repositorio.buscarUltimas(10).isEmpty());
    }

    @Test
    void quantidadeMaiorQueOTotalDevolveTudo() {
        repositorio.salvar(new Mensagem("ana", "única"));
        assertEquals(1, repositorio.buscarUltimas(50).size());
    }

    @Test
    void quantidadeNaoPositivaEInvalida() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarUltimas(0));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarUltimas(-1));
    }

    @Test
    void criarEsquemaEIdempotente() {
        assertDoesNotThrow(repositorio::criarEsquema);
        assertDoesNotThrow(repositorio::criarEsquema);
    }

    @Test
    void separarInstrucoesIgnoraComentariosEPontoEVirgulaFinal() {
        List<String> partes = JdbcMensagemRepository.separarInstrucoes(
                "-- comentário\nCREATE TABLE a (x INT);\n\n-- outro\nCREATE INDEX i ON a (x);\n");
        assertEquals(2, partes.size());
        assertEquals("CREATE TABLE a (x INT)", partes.get(0));
    }

    // ---- busca por texto (SQL com LIKE) ----

    @Test
    void buscarPorTextoIgnoraMaiusculasEDevolveEmOrdemCronologica() {
        repositorio.salvar(new Mensagem("ana", "Reunião às 10h"));
        repositorio.salvar(new Mensagem("bia", "bom dia"));
        repositorio.salvar(Mensagem.grupo("estudo", "caio", "reunião do grupo"));

        List<Mensagem> achadas = repositorio.buscarPorTexto("REUNIÃO", 10);

        assertEquals(List.of("Reunião às 10h", "reunião do grupo"),
                achadas.stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void buscarPorTextoTrataPorcentoEUnderscoreComoTextoComum() {
        repositorio.salvar(new Mensagem("ana", "desconto de 50% hoje"));
        repositorio.salvar(new Mensagem("ana", "desconto de 5000 hoje"));
        repositorio.salvar(new Mensagem("ana", "nome_do_arquivo"));
        repositorio.salvar(new Mensagem("ana", "nomeXdoXarquivo"));

        assertEquals(1, repositorio.buscarPorTexto("50%", 10).size());
        assertEquals(1, repositorio.buscarPorTexto("nome_do", 10).size());
    }

    @Test
    void buscarPorTextoRespeitaOLimiteComAsMaisRecentes() {
        for (int i = 1; i <= 5; i++) {
            repositorio.salvar(new Mensagem("ana", "item " + i));
        }

        assertEquals(List.of("item 4", "item 5"),
                repositorio.buscarPorTexto("item", 2).stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void buscarPorTextoRejeitaTermoVazioELimiteInvalido() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorTexto("  ", 5));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorTexto(null, 5));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorTexto("a", 0));
    }
}
