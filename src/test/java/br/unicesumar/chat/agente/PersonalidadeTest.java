package br.unicesumar.chat.agente;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalidadeTest {

    @Test
    void padraoNaoAcrescentaNada() {
        assertEquals("", Personalidade.PADRAO.instrucao());
    }

    @Test
    void outrosModosViramInstrucaoEntreColchetesDuplos() {
        for (Personalidade p : Personalidade.values()) {
            if (p != Personalidade.PADRAO) {
                assertTrue(p.instrucao().startsWith("[[") && p.instrucao().endsWith("]] "), p.name());
            }
        }
    }

    @Test
    void reconheceNomesSemAcentoECaixa() {
        assertEquals(Optional.of(Personalidade.DESCONTRAIDA), Personalidade.de("Descontraída"));
        assertEquals(Optional.of(Personalidade.PROFESSORA), Personalidade.de(" professora "));
        assertEquals(Optional.empty(), Personalidade.de("pirata"));
        assertEquals(Optional.empty(), Personalidade.de(null));
    }

    @Test
    void semInstrucaoRemoveOsColchetes() {
        assertEquals("que horas são?", Personalidade.semInstrucao(Personalidade.FORMAL.instrucao() + "que horas são?"));
        assertEquals("texto normal", Personalidade.semInstrucao("texto normal"));
    }

    @Test
    void agenteLocalIgnoraAInstrucaoDeEstilo() throws Exception {
        var local = new AgenteLocal();
        String comEstilo = local.responder(java.util.List.of(
                new Turno(Turno.Papel.USUARIO, Personalidade.FORMAL.instrucao() + "ajuda")));
        assertTrue(comEstilo.startsWith("Comandos:"), comEstilo);
    }
}
