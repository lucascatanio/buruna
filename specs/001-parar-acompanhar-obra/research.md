# Research: Parar de acompanhar uma obra

Decisões tomadas no plano da [spec](spec.md). Cada uma cita o código que a sustenta, para
quem revisar conferir sem reabrir a investigação.

## R1. O que conta como "capítulo vindo da fonte"

- **Decision**: uma versão de capítulo (`SourceChapter`) com `chapterId` preenchido. Vale
  para capítulo publicado (`READY`) e para capítulo registrado que ainda está baixando
  (`PENDING` com `chapterId`).
- **Rationale**: o capítulo (`work.Chapter`) não guarda de qual fonte veio. O único vínculo
  é `source_chapters.chapter_id`. A versão que falha o download perde o `chapterId`
  (`SourceChapter.fail`), quem sai do capítulo também (`detachFromChapter`), e capítulo
  apagado pelo ADM vira `NULL` pelo `ON DELETE SET NULL` (V32). Então `chapterId != null`
  é exatamente "há um capítulo na obra que depende desta fonte para o crédito".
- **Alternatives considered**: contar só `READY` (deixaria apagar um acompanhamento com
  capítulo em download, e o capítulo terminaria publicado sem vínculo nenhum); guardar a
  fonte no capítulo (migration em `work`, fora do escopo, e não resolve o histórico).

## R2. Apagar ou encerrar

- **Decision**: decidido na clarificação. Sem capítulo vindo da fonte, apaga o
  acompanhamento e as versões. Com capítulo, encerra (estado `STOPPED`).
- **Rationale**: ver spec, *Clarifications*. O encerramento é o que mantém o crédito.
- **Alternatives considered**: só apagar (perde o crédito), só encerrar (deixaria lixo do
  caso mais comum, o do Jujutsu Kaisen, guardado para sempre).

## R3. `STOPPED` como valor novo do enum, sem coluna nova

- **Decision**: `TrackedWorkStatus.STOPPED` e `ALTER TYPE tracked_work_status ADD VALUE
  'STOPPED'` numa migration só para isso (V38).
- **Rationale**: o status já decide quem o executor lê (`findBySourceAndStatus(..., ACTIVE)`
  em `SourcingRunner` e `RequestSourcingRunUseCase`), então `STOPPED` fica fora das rodadas
  sem mudar uma linha do executor. O valor novo de um enum do PostgreSQL não pode ser usado
  na transação que o cria; migration própria, como a V35 fez com `MANGA_DOWNLOADER`.
- **Alternatives considered**: coluna `stopped_at`/`stopped_by` (nada na spec lê isso;
  `updated_at` já registra quando); flag booleana separada (dois campos para um estado).

## R4. Onde o `STOPPED` precisa ser filtrado

Levantado por busca de todos os usos de `TrackedWorkRepository`:

| Uso | Hoje | Com a feature |
|---|---|---|
| `SourcingRunner.activeWorks`, `sync` | só `ACTIVE` | sem mudança |
| `RequestSourcingRunUseCase` | só `ACTIVE` | sem mudança |
| `ListTrackedWorksUseCase.list` | `findAllByOrderByCreatedAtDesc` | exclui `STOPPED` |
| `SourcingDigest.lines` (e-mail diário) | `findAllByOrderByCreatedAtDesc` | exclui `STOPPED` |
| `ImportWorkUseCase` (série já acompanhada) | `existsBySourceAndExternalId` | ignora `STOPPED` (FR-013) |
| `ImportWorkUseCase` (obra já vinculada ao MangaDex) | `existsByWorkIdAndSource` | ignora `STOPPED` (FR-013) |
| `ListTrackedWorksUseCase.sourceOf` (crédito) | qualquer acompanhamento do MangaDex | só com capítulo vindo dele, incluindo `STOPPED` (FR-006) |

- **Decision**: métodos derivados novos no Spring Data (`...AndStatusNot`,
  `findByStatusNotOrderByCreatedAtDesc`), sem `@Query`.
- **Rationale**: princípio I e II da constituição; consulta derivada não cruza contexto.

## R5. Ações do ADM numa obra encerrada

- **Decision**: o agregado trata `STOPPED` como terminal. `pause`, `resume`,
  `requestListing`, `trackLanguage`, `ignoreGroup` e `stopIgnoringGroup` lançam
  `InvalidSourceActionException` numa obra encerrada. No use case, o `lock()` de
  `ManageTrackedWorkUseCase` responde `TrackedWorkNotFoundException` para `STOPPED`, e as
  ações por versão (`retry`, `redownload`, `useVersion`) conferem o acompanhamento da versão
  do mesmo jeito.
- **Rationale**: a obra encerrada some da tela; para o ADM ela não existe mais (404). A
  guarda no agregado impede que um caminho esquecido reative a obra (`resume()` hoje põe
  `ACTIVE` sem olhar o estado atual). Princípio III: a invariante mora no agregado.
- **Alternatives considered**: só a guarda no use case (um `resume()` chamado de outro
  lugar reabriria a obra).

## R6. Não cruzar com a rodada

- **Decision**:
  1. A ação trava a linha da obra acompanhada (`lockById`), como pausar e retomar.
  2. O importador passa a registrar o capítulo **e** a anexá-lo à versão numa única
     transação, com o lock da obra acompanhada, e só se ela ainda estiver `ACTIVE` e a
     versão ainda existir. Fora disso, pula a versão.
- **Rationale**: o executor não segura lock durante a rodada inteira. Ele monta a lista de
  versões pendentes e baixa uma por uma (`SourcingRunner.drainOnce` →
  `SourceChapterImporter.run`). Hoje `register` grava o capítulo numa transação e
  `attachChapter` noutra. Se a ação acontecer entre a lista e o registro:
  - com remoção, a versão some, o capítulo é criado mesmo assim e termina publicado sem
    vínculo nenhum (crédito perdido, capítulo órfão de fonte);
  - com encerramento, um download novo começa depois da ação (contra o FR-004).
  Conferir o estado sob o lock imediatamente antes de registrar fecha as duas janelas. O
  lock cobre só conferir, inserir o capítulo e anexar, nunca o download (ADR-53,
  princípio VII). Download que já tinha registrado o capítulo antes da ação continua e
  publica: a versão já tem `chapterId`, então a ação foi pelo encerramento e a versão
  continua lá para receber o `markReady`.
- **Alternatives considered**: travar a obra acompanhada durante o download inteiro
  (seguraria a linha e uma conexão do pool por minutos); reler o status antes de cada
  versão sem lock (deixa a janela aberta entre a leitura e o registro).

## R7. Contrato da ação

- **Decision**: `POST /admin/sourcing/tracked/{id}/stop`, devolvendo
  `{ "outcome": "REMOVED" | "STOPPED", "importedChapters": n }`. A lista ganha
  `importedChapters` em cada obra acompanhada, para a confirmação dizer o que vai acontecer
  antes do clique (FR-014).
- **Rationale**: segue as ações que já existem (`/check`, `/pause`, `/resume`, todas
  `POST`). Um `DELETE` mentiria no caso do encerramento.
- **Alternatives considered**: endpoint de pré-visualização (`GET .../stop-preview`); a
  contagem já sai das versões que a lista carrega, então não precisa de chamada extra.

## R8. Confirmação no front

- **Decision**: `confirm()` do navegador com o texto do FR-014, como já faz "Ignorar grupo"
  em `TrackedWorkItem.tsx`.
- **Rationale**: princípio VI; o padrão da tela já é esse. Um diálogo próprio pode vir
  depois para todas as ações destrutivas de uma vez.
