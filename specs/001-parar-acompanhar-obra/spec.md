# Feature Specification: Parar de acompanhar uma obra

**Feature Branch**: `001-parar-acompanhar-obra`

**Created**: 2026-11-15

**Status**: Draft

**Issue**: [#156](https://github.com/lucascatanio/buruna/issues/156)

**Input**: User description: "Sourcing: parar de acompanhar uma obra. Hoje não há como deixar de acompanhar uma obra no sourcing, só pausar. O ADMIN precisa de uma ação 'Parar de acompanhar' na obra acompanhada em /admin/import, com confirmação que diz o que acontece com os capítulos. A obra sai da lista e o executor não a lista nem baixa mais nada dela. Capítulos já publicados continuam no ar, e o crédito da fonte na página da obra continua quando houver capítulo vindo dela. A ação não pode cruzar com uma rodada em andamento do executor."

## Clarifications

### Session 2026-11-15

- Q: Apagar o acompanhamento ou encerrá-lo num estado novo? E com capítulos baixados? →
  A: Os dois, conforme o caso. Sem capítulo vindo da fonte, apaga. Com capítulo, encerra,
  porque o capítulo não guarda de qual fonte veio e é o registro do acompanhamento que
  sustenta o crédito exigido pela AUP do MangaDex. (FR-011, FR-012, FR-014)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Parar de acompanhar uma obra que não trouxe nada (Priority: P1)

O ADM tem na lista de obras acompanhadas uma entrada que não serve mais: a mesma obra já
chega por outra fonte, e esta nunca baixou capítulo nenhum. Caso real: o Jujutsu Kaisen é
acompanhado pelo mangafire.to, que traz os capítulos em pt-BR, e pelo MangaDex, pausado e
sem capítulos porque o MangaDex não tem a obra em pt-BR. O ADM quer tirar a entrada do
MangaDex da lista de vez.

**Why this priority**: é o caso que motivou a issue e o único que acontece hoje. Resolve
sozinho a lista poluída, sem nenhuma decisão sobre capítulos já publicados.

**Independent Test**: com uma obra acompanhada sem capítulos baixados, o ADM usa "Parar de
acompanhar", confirma, e a entrada some da lista; as rodadas seguintes não consultam mais
aquela fonte para aquela obra.

**Acceptance Scenarios**:

1. **Given** uma obra acompanhada (ativa ou pausada) sem capítulo baixado, **When** o ADM
   escolhe "Parar de acompanhar", **Then** aparece uma confirmação dizendo que nenhum
   capítulo veio dessa fonte e que o acompanhamento será removido.
2. **Given** a confirmação aberta, **When** o ADM confirma, **Then** a obra sai da lista de
   acompanhadas e a lista mostra que a ação deu certo.
3. **Given** a confirmação aberta, **When** o ADM cancela, **Then** nada muda.
4. **Given** uma obra que deixou de ser acompanhada, **When** a próxima rodada automática
   ou manual roda, **Then** essa fonte não é listada nem baixa nada para essa obra.
5. **Given** a mesma obra acompanhada por outra fonte, **When** o ADM para de acompanhar
   uma delas, **Then** a outra continua acompanhada como antes.

---

### User Story 2 - Parar de acompanhar uma obra que já trouxe capítulos (Priority: P2)

O ADM quer parar de acompanhar uma fonte que já publicou capítulos na obra (a fonte
piorou, o grupo parou, outra fonte cobre melhor). Os leitores não podem perder nada do que
já está no ar, e a página da obra continua creditando a fonte de onde vieram os capítulos.

**Why this priority**: ainda não aconteceu, mas vai acontecer assim que houver mais de uma
fonte boa para a mesma obra. Aqui o acompanhamento é encerrado, não apagado, para o
crédito continuar (FR-012).

**Independent Test**: com uma obra acompanhada que já publicou capítulos, o ADM para de
acompanhar e confere que os capítulos seguem legíveis, com progresso e histórico de quem
lê intactos, e que o crédito da fonte continua na página da obra.

**Acceptance Scenarios**:

1. **Given** uma obra acompanhada com capítulos baixados, **When** o ADM escolhe "Parar de
   acompanhar", **Then** a confirmação diz quantos capítulos vieram dessa fonte, que eles
   continuam no ar e que a fonte continua creditada.
2. **Given** a ação confirmada, **When** um leitor abre a obra, **Then** os capítulos que
   vieram da fonte continuam listados e legíveis, e o progresso e o histórico dele
   continuam valendo.
3. **Given** a ação confirmada, **When** qualquer pessoa abre a página da obra, **Then** o
   crédito da fonte continua aparecendo enquanto houver capítulo vindo dela.
4. **Given** a ação confirmada, **When** o ADM olha a lista de acompanhadas, **Then** a
   obra não aparece mais nela, nem nos avisos.

---

### User Story 3 - Não cruzar com uma rodada em andamento (Priority: P3)

O executor pode estar listando ou baixando capítulos da obra no momento em que o ADM pede
para parar de acompanhá-la. A ação e a rodada não podem deixar a obra num estado
inconsistente (meio removida, ou com download novo chegando depois da remoção).

**Why this priority**: raro, mas sem isso a ação não é confiável.

**Independent Test**: dispara-se a ação enquanto uma rodada processa a mesma obra
acompanhada; uma das duas espera a outra terminar, e o resultado final é o mesmo de se
tivessem acontecido uma depois da outra.

**Acceptance Scenarios**:

1. **Given** uma rodada processando a obra acompanhada, **When** o ADM confirma "Parar de
   acompanhar", **Then** a ação só conclui depois que a rodada solta aquela obra, e nenhum
   download dessa fonte começa para ela depois disso.
2. **Given** a ação concluída, **When** uma rodada que já tinha começado chega na obra,
   **Then** ela pula a obra sem erro.

---

### Edge Cases

- A obra acompanhada está pausada (pelo ADM ou sozinha, por falhas): a ação funciona igual.
- Dois ADMs (ou duas abas) confirmam ao mesmo tempo: a segunda confirmação não dá erro
  genérico; a obra já não está acompanhada e a mensagem diz isso.
- O ADM quer voltar a acompanhar a mesma série na mesma fonte depois: o import normal
  aceita, sem bloquear como "já acompanhada", tanto depois de uma remoção quanto de um
  encerramento (FR-013).
- Um capítulo dessa fonte está em processamento no momento da ação: ele conta como
  capítulo vindo da fonte, então o caminho é o encerramento, e o capítulo termina normal.
- A fonte é a única que trouxe capítulos para a obra: o crédito continua (Story 2).
- A obra acompanhada tem avisos pendentes (versão removida na fonte, páginas trocadas):
  os avisos saem junto com a obra da lista e não entram mais no e-mail diário.
- Um usuário sem papel de ADM tenta a ação: é recusada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O ADM MUST poder parar de acompanhar qualquer obra acompanhada, ativa ou
  pausada, a partir da lista de obras acompanhadas em `/admin/import`.
- **FR-002**: O sistema MUST pedir confirmação antes de concluir, dizendo quantos capítulos
  vieram dessa fonte e o que acontece com eles.
- **FR-003**: Depois de concluída, a obra MUST sair da lista de obras acompanhadas e dos
  avisos e do e-mail diário do sourcing.
- **FR-004**: Depois de concluída, o executor MUST não listar nem baixar nada dessa fonte
  para essa obra, nas rodadas automáticas e nas pedidas pelo ADM.
- **FR-005**: Capítulos já publicados que vieram da fonte MUST continuar no ar, com o
  progresso e o histórico de leitura intactos. Parar de acompanhar nunca apaga capítulo.
- **FR-006**: O crédito da fonte na página da obra MUST continuar enquanto houver capítulo
  publicado vindo dela, e MUST não aparecer por causa de um acompanhamento que não trouxe
  capítulo nenhum.
- **FR-007**: A ação MUST ser serializada com a rodada do executor na mesma obra
  acompanhada: nenhuma das duas vê a outra pela metade.
- **FR-008**: Outras fontes da mesma obra MUST continuar acompanhadas sem mudança.
- **FR-009**: O ADM MUST poder importar de novo a mesma série na mesma fonte depois de
  parar de acompanhá-la.
- **FR-010**: Só o papel ADMIN MUST conseguir executar a ação.
- **FR-011**: Quando nenhum capítulo veio da fonte, o acompanhamento MUST ser apagado,
  junto com as versões listadas e os grupos ignorados dele. Não sobra rastro na lista nem
  no crédito.
- **FR-012**: Quando algum capítulo veio da fonte, o acompanhamento MUST ser **encerrado**:
  fica guardado, com as versões e o vínculo de cada capítulo, mas não aparece mais na
  lista de acompanhadas, não é verificado nem baixa nada, e não gera aviso. É esse
  registro que mantém o crédito da fonte (FR-006).
- **FR-013**: Um acompanhamento encerrado MUST não contar como "já acompanhada" num import
  novo da mesma série na mesma fonte (FR-009). O import novo cria outro acompanhamento, e
  os números de capítulo que já existem na obra continuam valendo como hoje (não são
  baixados de novo).
- **FR-014**: A confirmação MUST dizer qual dos dois caminhos vai acontecer: "nenhum
  capítulo veio desta fonte, o acompanhamento será removido" ou "N capítulos vieram desta
  fonte e continuam no ar; o acompanhamento será encerrado e a fonte continua creditada".

### Key Entities

- **Obra acompanhada**: o vínculo entre uma obra do catálogo e uma série numa fonte
  externa, com os idiomas e grupos escolhidos, o estado (ativa, pausada ou encerrada) e os
  avisos. Encerrada é o estado novo desta feature.
- **Versão de capítulo na fonte**: cada versão que a fonte listou para a obra, com o
  estado (pendente, baixada, falhou, ignorada) e o capítulo do catálogo que ela gerou.
- **Capítulo** (catálogo): pertence à obra, não à fonte. É o que o leitor vê.
- **Crédito da fonte**: a indicação "Fonte: …" na página da obra, exigida pelos termos de
  uso do MangaDex para conteúdo vindo dele.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: O ADM tira uma obra acompanhada da lista em até 3 cliques, sem mexer no banco
  nem pedir ajuda.
- **SC-002**: Depois do deploy, a entrada do MangaDex do Jujutsu Kaisen sai da lista, e a do
  mangafire.to continua trazendo capítulos novos.
- **SC-003**: 100% dos capítulos publicados antes da ação continuam legíveis depois dela,
  com o progresso de quem lê preservado.
- **SC-004**: Nenhuma rodada depois da ação consulta a fonte para aquela obra.
- **SC-005**: Ação e rodada simultâneas na mesma obra terminam sem erro e sem download
  órfão em 100% das execuções do teste de concorrência.

## Assumptions

- A ação é só do ADM, na tela que já existe para as obras acompanhadas; não há tela nova.
- "Parar de acompanhar" é definitivo do ponto de vista do ADM: não existe "desfazer" nem
  tela de acompanhamentos encerrados; para voltar, importa-se de novo (FR-009).
- O e-mail diário e os avisos só tratam de obras acompanhadas; uma obra que saiu da lista
  não gera mais aviso.
- O crédito da fonte depende dos capítulos publicados que vieram dela, não da existência
  do acompanhamento. Hoje o crédito do MangaDex aparece até para acompanhamento sem
  capítulo; isso passa a ser corrigido (FR-006).
- Capítulo de outra origem (upload, outra fonte) com o mesmo número não é afetado.
