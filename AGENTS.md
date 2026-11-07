# AGENTS.md — Chat Multiusuário (POO, UniCesumar)

Instruções para qualquer assistente de IA que mexa neste repositório.

## Projeto
Chat multiusuário em Java 17 / Maven: sockets (thread por cliente), histórico em H2 via JDBC,
conversa privada, grupos e a assistente UCI (IA por API). Clientes: Swing (`ClienteGui`) e terminal (`ClienteChat`).

## Comandos
- `mvn test` — testes JUnit 5
- `mvn package` — gera `target/chat.jar`
- Servidor: `java -cp target/chat.jar br.unicesumar.chat.server.Servidor [porta]`
- Logs de comandos: `logs/chat-comandos.log` (quem digitou, o quê, e o motivo de cada erro)

## Regras de arquitetura
- `model` não importa `java.sql`, `java.io`, `java.net`, `javax.swing` nem `persistence`/`server`/`client`.
- Toda persistência passa por uma interface Repository (`MensagemRepository`, `GrupoRepository`); há sempre um adaptador JDBC e um falso em memória para os testes.
- `ConexaoChat` (rede) não usa Swing; `AplicacaoChat` (controlador) não tem regra de negócio; as telas não tratam `SQLException`.
- Estado compartilhado da sala só é lido/alterado com `synchronized (clientes)` em `SalaChat`. Chamadas lentas (IA, banco de grupos) nunca seguram esse lock além do necessário.
- Moderação é a porta `ModeradorDeMensagens` (adaptador `ModeradorPorLista`); a sala só chama a interface.
- Tema da janela: `Tema.aplicar(...)` troca a paleta e `AplicacaoChat.reaplicarAparencia()` recria as telas e reexibe a transcrição; cores nunca ficam fixas nas telas (use os tokens de `Tema`).
- Linhas `@@` (`@@STATUS`, `@@DIGITANDO`, ...) só são enviadas após `/modo gui`; o terminal nunca as recebe. O formato fica só em `Protocolo`/`Interpretador`.
- Tecnologia nova = adaptador novo (`AgenteIA`, `Repository`); o domínio não muda.
- Segredos (chave da API) vêm de variável de ambiente ou `uci.properties` (ignorado pelo Git); nunca no código.

## Regras de código e testes
- Testes seguindo FIRST; nada de `Thread.sleep` para sincronizar (use `Espera`, `CountDownLatch`, `awaitTermination`).
- Concorrência testada com `ExecutorService` + `@RepeatedTest`.
- Exceções não são engolidas: ou tratadas, ou registradas com `Logger` (nível adequado).
- Objetos de valor imutáveis (`Mensagem`, `Usuario`); coleções expostas como cópias.
- Todo erro de comando digitado pelo usuário vira "! motivo" para ele e uma linha no `LogComandos`.

## Uso de IA
Partes deste projeto foram geradas com apoio de IA e revisadas pelo grupo; as decisões de arquitetura são do grupo.
Declare no vídeo e no relatório quais trechos usaram IA.
