# Relatório — Chat Multiusuário com Histórico

**Disciplina:** Programação Orientada a Objetos (Java) — UniCesumar/UNIPA Curitiba, 2º semestre de 2026
**Aluno(a):** _<preencher nome e RA>_

## 1. Visão geral

O sistema é um chat cliente-servidor em Java. O servidor aceita várias conexões simultâneas por
socket TCP; toda mensagem enviada por um cliente é gravada em banco de dados (JDBC) e
retransmitida aos demais clientes conectados. Ao conectar, o cliente recebe as últimas mensagens
do histórico.

## 2. Arquitetura

```
 ClienteChat ──socket──►  Servidor ──► (1 thread por conexão) ClienteHandler ─┐
 ClienteChat ──socket──►  (accept)                                            ├─► SalaChat ─► MensagemRepository ─► banco
 ClienteChat ──socket──►                                                      ┘   (lista de clientes + broadcast)   (JDBC)
```

| Pacote | Classe | Responsabilidade única |
|---|---|---|
| `server` | `Servidor` | Socket de escuta; aceita conexões e delega cada uma a uma thread |
| `server` | `ClienteHandler` | Atende uma conexão: lê do socket, interpreta comandos e entrega saída ao cliente |
| `server` | `SalaChat` | Lista sincronizada de clientes, persistência e broadcast (sem conhecer sockets) |
| `server` | `Destinatario` | Interface que isola a sala da rede (permite testar sem sockets) |
| `model` | `Mensagem`, `Usuario` | Entidades imutáveis e regras de negócio (validação, formatação) |
| `persistence` | `MensagemRepository` / `JdbcMensagemRepository` | Único ponto que contém SQL/JDBC |
| `client` | `ConexaoChat` | Lógica de rede do cliente (conectar, nome, enviar, receber), sem Swing |
| `client` | `TelaInicio`, `TelaChat` | Telas Swing: só desenham e avisam o controlador dos cliques |
| `client` | `AplicacaoChat` | Controlador da interface: alterna as telas, conecta em segundo plano |
| `client` | `ClienteGui`, `ClienteChat` | Pontos de entrada do cliente gráfico e do cliente de console |

**Coesão e acoplamento.** A `SalaChat` depende apenas de duas interfaces (`Destinatario` e
`MensagemRepository`), não de `Socket` nem de JDBC. Por isso a lógica de broadcast e de
histórico é testada com objetos falsos, sem rede e sem banco.

**Protocolo.** Texto em UTF-8, uma linha por mensagem. O primeiro texto enviado é o nome do
usuário; depois, cada linha é uma mensagem ou um comando (`/usuarios`, `/ajuda`, `/sair`).
Por isso `Mensagem` rejeita conteúdo com quebra de linha.

## 3. Como a concorrência foi tratada

**Threads.** `Servidor.aceitarConexoes()` bloqueia em `accept()` e, a cada conexão, cria uma
thread com um `ClienteHandler` (`Runnable`). Um cliente lento ou desconectado só afeta a sua
própria thread.

**Recursos compartilhados.** Todas as threads de cliente disputam (a) a lista de clientes
conectados e (b) o banco de dados.

**Lista de clientes.** É uma `Collections.synchronizedList`. Como entrar, sair e publicar são
operações *compostas* (checar nome + adicionar; salvar + percorrer a lista), elas são feitas
dentro de `synchronized (clientes)`, o idioma recomendado pela documentação para esse caso.
Isso garante que:

1. **Ordem única:** todos os clientes veem as mensagens na mesma ordem em que foram gravadas
   (a gravação e o broadcast acontecem sob o mesmo lock).
2. **Sem perda nem duplicação no histórico:** ao entrar, a leitura do histórico e o cadastro na
   lista são atômicos em relação às publicações. Uma mensagem publicada "no meio" não se perde
   (seria entregue pelo broadcast) nem chega duas vezes (já estaria no histórico).
3. **Sem `ConcurrentModificationException`:** o broadcast percorre uma *cópia* da lista, pois
   um cliente com falha é removido durante o laço.
4. **Nomes únicos:** a verificação de nome em uso e a inserção são atômicas (dois clientes não
   conseguem registrar o mesmo nome ao mesmo tempo).

**Banco de dados.** `JdbcMensagemRepository` não guarda estado e cada operação abre e fecha a
sua própria conexão (try-with-resources), então não há conexão compartilhada entre threads.

**Escrita no socket sem bloquear a sala.** Cada `ClienteHandler` tem uma fila de saída
(`LinkedBlockingQueue` limitada) e uma thread escritora que a esvazia. A sala chama
`Destinatario.enviar()`, que apenas enfileira (não bloqueia). Assim, um cliente com a rede
travada não segura o lock da sala e não atrasa os outros. Se a fila encher, o cliente lento é
desconectado. A escrita no socket é feita sempre por uma única thread, o que evita linhas
misturadas.

**Desconexões abruptas.** Erros de E/S (`IOException`) ou fim do fluxo (`readLine() == null`)
levam ao bloco `finally` do handler, que remove o cliente da sala (avisando os demais), fecha o
socket e encerra a thread escritora. Se um envio falha, a sala também remove o destinatário.
O servidor e os demais clientes continuam funcionando.

**Falha no banco.** Se o banco falhar ao gravar, o erro é registrado em log e a mensagem ainda é
retransmitida; se falhar ao ler o histórico, o cliente entra sem histórico.

**Decisão de projeto (trade-off).** A gravação no banco é feita *dentro* do lock da sala, para
garantir a ordem única do item 1. O custo é que as publicações são serializadas (uma por vez).
Para um chat de sala única isso é aceitável e simples de raciocinar.

### Concorrência no cliente gráfico

O Swing só pode ser alterado pela thread de eventos (EDT), e a rede bloqueia. Por isso:
(1) a conexão é feita em um `SwingWorker`, então a janela não congela enquanto conecta;
(2) `ConexaoChat` lê do socket em uma thread própria e entrega cada linha ao `AplicacaoChat`,
que a reenvia para a EDT com `SwingUtilities.invokeLater` antes de tocar em qualquer
componente. Todo o estado da interface (conexão atual, nome, lista online) só é lido e
alterado na EDT, o que dispensa locks.

## 4. Interface gráfica

Fluxo: **Início** → "Entrar no chat" → **Chat**. No chat, "Início" volta à tela inicial *sem
desconectar* (lá aparece "Voltar ao chat"), e "Sair do chat" desconecta de fato. A tela mostra
o histórico ao entrar, as mensagens coloridas por usuário, a lista de usuários online (atualizada
por `/usuarios` quando alguém entra ou sai) e mostra erros do servidor na própria tela. O cliente
reutiliza as regras de `Usuario` e `Mensagem` para validar nome e texto antes de enviar.

Visual: a conversa deixou de ser um `JTextPane` e passou a ser uma pilha de componentes desenhados à mão
(balões, avisos), que se ajustam à largura da janela (`Scrollable` que acompanha a largura + um
`LayoutManager` próprio que pergunta a altura de cada linha). A quebra de linhas usa a mesma métrica de
fonte do desenho, para o texto nunca passar do balão. Cores e fontes ficam centralizadas em `Tema`.

## 4.1 Privado, grupos e UCI

**Mensagens e tipos.** `Mensagem` ganhou `tipo` (GERAL, GRUPO, PRIVADA) e `destino`. A tabela
`mensagem` recebeu as colunas `tipo`/`destino` (migração automática) e há `grupo` e `grupo_membro`.
O histórico geral nunca mistura grupo/privado; o privado é consultado nos dois sentidos, sem
diferenciar maiúsculas.

**Concorrência.** Continua havendo **um único lock** (`synchronized (clientes)` em `SalaChat`) que
protege a lista de clientes, os grupos e a entrega; assim, entrar/sair de grupo e enviar mensagem
nunca se cruzam e todos veem a mesma ordem. O estado dos grupos é gravado no banco (write-through)
e só é alterado sob esse lock.

**UCI.** A chamada de rede à API é **lenta**, então nunca ocorre dentro do lock: `ServicoUci` usa
um pool de threads próprio e só pega o lock para publicar a resposta. Há uma pergunta por vez por
conversa (as demais recebem aviso) e fila limitada. `AgenteIA` é a interface (padrão Strategy): `AgenteCompativel` (formato "chat completions": Ollama local ou
serviços com camada gratuita), `AgenteAnthropic` e `AgenteLocal` (plano B offline) a implementam, todos com
`java.net.http.HttpClient`, e `Servidor.escolherAgente` decide pelo ambiente. A UCI tem nome reservado, então
ninguém consegue se passar por ela.

**Protocolo.** Uma linha por mensagem, definida só em `Protocolo`/`Interpretador`. Linhas de controle
(`@@USUARIOS`, `@@GRUPOS`...) só vão a clientes que enviaram `/modo gui`; clientes de console nunca
as veem. Nomes não aceitam espaço, `:` nem `#`, então o texto de um usuário não forja linhas do protocolo.

**Privacidade.** Perguntas à UCI saem para o provedor da API; privado e grupos ficam em texto puro no banco.

## 5. Persistência

Tabela `mensagem(id, remetente, conteudo, horario)` — script em `src/main/resources/schema.sql`
(executado automaticamente ao subir o servidor, de forma idempotente). `salvar()` devolve a
mensagem com o id gerado; `buscarUltimas(n)` consulta com `ORDER BY id DESC LIMIT ?` e inverte o
resultado para a ordem cronológica. Banco padrão: H2 em arquivo; configurável por variáveis de
ambiente.

## 6. Testes

- **Modelo** (`MensagemTest`, `UsuarioTest`): formatação, validações, limites.
- **Sala** (`SalaChatTest`): broadcast aos demais (não ao remetente), nome repetido, histórico
  limitado e em ordem, cliente que cai, falha de banco, **publicações concorrentes** (8 threads ×
  50 mensagens: nada se perde e a ordem é igual para todos) e entradas/saídas concorrentes.
  Usa `DestinatarioFalso` e `RepositorioEmMemoria`; não depende de rede nem de banco.
- **Repositório** (`JdbcMensagemRepositoryTest`): salvar, ordem e limite do histórico, validações,
  com H2 em memória.
- **Integração** (`ServidorIntegracaoTest`): servidor real em localhost com 3 clientes por socket,
  queda abrupta, histórico ao conectar e nome duplicado.
- **Privado, grupos e UCI** (`SalaChatPrivadoTest`, `SalaChatGrupoTest`, `SalaChatUciTest`,
  `ComandosChatTest`, `GrupoTest`, `InterpretadorTest`): isolamento (quem não é membro não recebe),
  histórico por tipo, concorrência em grupos, UCI com agente falso controlável (`AgenteFalso`),
  falhas e fila cheia da UCI. Os testes foram validados por **mutação** (defeitos injetados de
  propósito, como "entregar grupo a todos" ou "vazar privado", são detectados).
- **Agente** (`AgenteAnthropicTest`, `AgenteLocalTest`, `JsonTest`, `ServicoUciTest`): corpo e leitura
  das respostas da API sem rede real, erros HTTP, modo local.
- **Banco** (`JdbcGrupoRepositoryTest`, `JdbcMensagemTiposTest`, `GrupoRepositoryMemoriaTest`):
  grupos/membros, rollback, tipos de mensagem e migração de banco antigo.
- **Interface** (`AplicacaoChatTest`, `ConversaTest`): a GUI sem janela visível contra um servidor
  real: abas, privado, grupos, UCI "digitando…", Início/Voltar/Sair.
- **Cliente** (`ConexaoChatTest`): conexão, histórico, nome em uso/inválido, servidor inacessível,
  desconexão pedida pelo usuário e queda do servidor, sem abrir janelas.

## 7. Limitações e melhorias possíveis

- Grupos são **abertos** (qualquer um entra); não há convite, administrador nem remoção de membros.
- Mensagem privada só para quem está **online** (não há caixa de entrada offline).
- A UCI atende uma pergunta por vez em cada conversa.
- Não há autenticação: o nome é apenas um apelido.
- Um cliente com a rede travada é desconectado quando sua fila enche (500 linhas).
- A interface é testada sem janela real (headless); o visual foi conferido por capturas de tela.
- Sem criptografia (TLS) nem autenticação.

## Log de comandos e terminal (revisão pós-aulas)

- `LogComandos` registra, em `logs/chat-comandos.log` e no console, cada comando (INFO), cada recusa com o motivo (AVISO) e cada falha inesperada com pilha (ERRO). O conteúdo de conversas comuns não é gravado.
- `ComandosChat` captura qualquer `RuntimeException`: o usuário recebe `! motivo` e continua conectado (antes, um erro inesperado derrubava a conexão).
- `ClienteChat` usa a codificação do terminal (cp850/Cp1252 no Windows) em vez de forçar UTF-8; sem isso, nomes com acento eram recusados como inválidos.
- Adicionados `AGENTS.md`, `sql/criar_tabelas.sql` (com teste de igualdade com `schema.sql`), diagrama Mermaid, tabela de termos e revisão de concorrência no README.
- Uso de IA: o projeto foi desenvolvido com apoio de IA (Claude) e revisado pelo grupo.

## Personalização e utilidades (revisão final)

Acrescentados: status do usuário, aviso "digitando", `/buscar` (consulta `LIKE` com escape, só no que o usuário pode ver),
`/exportar`, modos da UCI (`Personalidade`) e utilidades (resumir/traduzir/explicar, resposta privada), moderação por lista
(porta + adaptador), remoção de membro pelo criador do grupo e, na janela, tema claro/escuro, fonte, modo compacto,
cor própria, não lidas, som e silenciar. O estado novo da sala segue o lock único; `SalaChatConcorrenciaTest` verifica
12 threads simultâneas (10 repetições) sem perda de mensagens e com ordem igual para todos.

Verificação: 348 testes executados num executor JUnit simplificado (javac), incluindo ponta a ponta com sockets reais e captura
de imagens da janela nos dois temas. **Não** foi possível executar aqui: Maven/JUnit reais, H2 (testes `Jdbc*`, incluindo
`buscarPorTexto`) e chamadas reais à API de IA — rode `mvn test` na máquina do grupo.
Não implementado: reações/responder, login com senha, envio de arquivos/imagens, fixar salas.
