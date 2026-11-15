---
description: "Tarefas da feature 001: parar de acompanhar uma obra"
---

# Tasks: Parar de acompanhar uma obra

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/admin-sourcing.md](contracts/admin-sourcing.md), [quickstart.md](quickstart.md)

**Issue**: [#156](https://github.com/lucascatanio/buruna/issues/156)

**Tests**: obrigatórios. A constituição (princípio V) exige teste para toda mudança de
comportamento, e a issue lista os cenários. Os testes vêm antes da implementação em cada
fase e precisam falhar primeiro.

**Organization**: por user story, para cada uma ser entregue e testada sozinha.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: user story da spec (US1, US2, US3)
- Caminhos a partir da raiz do repositório. `sourcing/` abrevia
  `backend/src/main/java/com/buruna/sourcing/`, e `sourcing-test/` abrevia
  `backend/src/test/java/com/buruna/sourcing/`

---

## Phase 1: Setup

**Purpose**: nada a instalar. Projeto, dependências e CI já existem.

- [ ] T001 Conferir que `./mvnw clean test` (em `backend/`) e `npm run build` (em `frontend/`) estão verdes na branch antes de mexer, para a régua começar limpa

---

## Phase 2: Foundational (pré-requisitos de todas as stories)

**Purpose**: o estado `STOPPED` no banco e no agregado, e a regra "capítulo vindo da fonte".

**⚠️ CRITICAL**: nenhuma story começa antes desta fase.

### Testes da fase

- [ ] T002 [P] Em `sourcing-test/domain/TrackedWorkTest.java`, escrever (AAA, `should_when`): `shouldBecomeStopped_whenStoppedFromActive`, `shouldBecomeStopped_whenStoppedFromPaused`, `shouldClearListingRequest_whenStopped`, `shouldThrowInvalidSourceAction_whenStoppingTwice` e um teste por ação proibida numa obra encerrada: `pause`, `resume`, `requestListing`, `trackLanguage`, `ignoreGroup`, `stopIgnoringGroup` lançam `InvalidSourceActionException`
- [ ] T003 [P] Em `sourcing-test/domain/SourceChapterTest.java`, escrever `shouldHaveChapter_whenChapterAttached`, `shouldNotHaveChapter_whenDownloadFailed` (depois de `fail`), `shouldNotHaveChapter_whenDetached`

### Implementação da fase

- [ ] T004 Criar `backend/src/main/resources/db/migration/V38__tracked_work_stopped.sql` com só `ALTER TYPE tracked_work_status ADD VALUE 'STOPPED';` e um comentário dizendo que fica sozinha porque o valor novo não pode ser usado na transação que o cria (mesmo motivo da V35)
- [ ] T005 Acrescentar `STOPPED` em `sourcing/domain/TrackedWorkStatus.java` e atualizar o Javadoc do enum: "STOPPED: o ADM parou de acompanhar; a obra sai da lista, do executor e dos avisos, e o registro fica para o crédito da fonte. Terminal."
- [ ] T006 Em `sourcing/domain/TrackedWork.java`: método `stop()` (de `ACTIVE` ou `PAUSED` para `STOPPED`, `listingRequested = false`; de `STOPPED` lança `InvalidSourceActionException("Esta obra já não é acompanhada.")`), `isStopped()`, e uma guarda privada `requireNotStopped()` chamada no início de `pause`, `resume`, `requestListing`, `trackLanguage`, `ignoreGroup` e `stopIgnoringGroup`. Faz T002 passar
- [ ] T007 [P] Em `sourcing/domain/SourceChapter.java`, método `hasChapter()` (`chapterId != null`), com Javadoc citando research R1. Faz T003 passar
- [ ] T008 [P] Em `sourcing/persistence/TrackedWorkRepository.java`, consultas derivadas (sem `@Query`): `findByStatusNotOrderByCreatedAtDesc(TrackedWorkStatus status)`, `existsBySourceAndExternalIdAndStatusNot(SourceName source, String externalId, TrackedWorkStatus status)`, `existsByWorkIdAndSourceAndStatusNot(UUID workId, SourceName source, TrackedWorkStatus status)`

**Checkpoint**: `TrackedWorkTest` e `SourceChapterTest` verdes; `ArchitectureTest` verde.

---

## Phase 3: User Story 1 - Parar de acompanhar uma obra que não trouxe nada (Priority: P1) 🎯 MVP

**Goal**: o ADM tira da lista um acompanhamento sem capítulo vindo da fonte, e ele é apagado.

**Independent Test**: obra acompanhada sem capítulo → "Parar de acompanhar" → some da lista,
as rodadas não a consultam, reimportar a mesma série funciona.

### Testes da US1

- [ ] T009 [P] [US1] Em `sourcing-test/SourcingIntegrationTest.java`: `shouldRemoveTrackedWorkAndVersions_whenStoppedWithoutChapters` (POST `/api/admin/sourcing/tracked/{id}/stop` → 200 `{"outcome":"REMOVED","importedChapters":0}`; `tracked_works` e `source_chapters` da obra sem linhas; a lista não traz mais a obra), `shouldKeepOtherSourceTracked_whenOneSourceStopped` (FR-008), `shouldAllowReimport_whenTrackedWorkWasRemoved` (FR-009), `shouldReturnNotFound_whenStoppingRemovedTrackedWork` (segunda chamada → 404) e `shouldForbidStop_whenUserIsNotAdmin` (READER → 403)

### Implementação da US1

- [ ] T010 [P] [US1] Criar `sourcing/application/StopTrackingResult.java`: `record StopTrackingResult(String outcome, long importedChapters)` com constantes `REMOVED` e `STOPPED`
- [ ] T011 [US1] Em `sourcing/application/ManageTrackedWorkUseCase.java`, método `@Transactional StopTrackingResult stop(UUID trackedWorkId)`: `lock(trackedWorkId)`, carrega as versões (`sourceChapterRepository.findByTrackedWorkId`), conta `hasChapter()`; com zero, `sourceChapterRepository.deleteAll(versions)` e `trackedWorkRepository.delete(tracked)` e devolve `REMOVED`. (O caminho com capítulos entra na US2; até lá, com capítulos, lança `InvalidSourceActionException`.) Javadoc citando a spec e a ADR-53
- [ ] T012 [US1] Em `sourcing/web/AdminSourcingController.java`, `@PostMapping("/tracked/{id}/stop") public StopTrackingResult stop(@PathVariable UUID id)` delegando a `ManageTrackedWorkUseCase.stop`
- [ ] T013 [P] [US1] Em `sourcing/application/TrackedWorkResponse.java` e no mapeamento em `sourcing/application/ListTrackedWorksUseCase.java`, campo `long importedChapters` = versões com `hasChapter()` (as versões já são carregadas em `toResponse`)
- [ ] T014 [P] [US1] Em `frontend/src/types/sourcing.ts`, `importedChapters: number` em `TrackedWork` e o tipo `StopTrackingResult { outcome: "REMOVED" | "STOPPED"; importedChapters: number }`; em `frontend/src/api/sourcingApi.ts`, `stopTrackingWork(id: string): Promise<StopTrackingResult>` (`POST /admin/sourcing/tracked/${id}/stop`)
- [ ] T015 [US1] Em `frontend/src/components/sourcing/TrackedWorkItem.tsx`, botão "Parar de acompanhar" ao lado de Pausar/Retomar, com `confirm()` (como "Ignorar grupo") e o texto do FR-014: com `importedChapters === 0`, "Nenhum capítulo veio desta fonte. O acompanhamento será removido e a fonte deixa de ser verificada."; senão, "N capítulos vieram desta fonte e continuam no ar. O acompanhamento será encerrado e a fonte continua creditada na página da obra." Depois do sucesso, toast e recarga da lista; no 404, toast "Esta obra não está mais acompanhada." e recarga

**Checkpoint**: US1 funciona sozinha. Já dá para tirar o Jujutsu Kaisen do MangaDex.

---

## Phase 4: User Story 2 - Parar de acompanhar uma obra que já trouxe capítulos (Priority: P2)

**Goal**: com capítulo vindo da fonte, o acompanhamento é encerrado, os capítulos ficam no
ar e o crédito continua.

**Independent Test**: obra acompanhada com capítulos publicados → "Parar de acompanhar" →
some da lista e do e-mail, capítulos legíveis, `GET /works/{id}/source` continua 200.

### Testes da US2

- [ ] T016 [P] [US2] Em `sourcing-test/SourcingIntegrationTest.java`: `shouldStopAndKeepChapters_whenStoppedWithChapters` (200 `{"outcome":"STOPPED","importedChapters":N}`; capítulos continuam `PUBLISHED` e no manifesto do leitor; progresso de um leitor continua), `shouldKeepSourceCredit_whenTrackedWorkStoppedWithChapters`, `shouldHideStoppedTrackedWork_fromList`, `shouldReturnNotFound_whenActingOnStoppedTrackedWork` (check, pause, resume, alerts, retry de uma versão → 404), `shouldAllowReimport_whenTrackedWorkWasStopped` (FR-013) e `shouldNotCreditMangaDex_whenTrackedWorkHasNoChapters` (FR-006: hoje responde 200, passa a 204)
- [ ] T017 [P] [US2] Em `sourcing-test/SourcingSyncIntegrationTest.java`: `shouldSkipStoppedTrackedWork_whenSyncRuns` (nenhuma listagem pedida ao `ExternalSource` mockado) e `shouldLeaveStoppedTrackedWorkOutOfDigest` (aviso pendente de uma obra encerrada não entra no e-mail diário)

### Implementação da US2

- [ ] T018 [US2] Em `sourcing/application/ManageTrackedWorkUseCase.java`, completar `stop`: com `importedChapters > 0`, `tracked.stop()` e devolver `STOPPED` (remove o `InvalidSourceActionException` provisório da T011)
- [ ] T019 [US2] Em `sourcing/application/ManageTrackedWorkUseCase.java`, `lock()` e a leitura de `alerts`, `groups` e `untrackedSourceLanguages` lançam `TrackedWorkNotFoundException` para obra `STOPPED` (um helper `find()` para as leituras); `version()` e `sourceOf(version)` herdam isso por usarem `lock()`
- [ ] T020 [P] [US2] Em `sourcing/application/ListTrackedWorksUseCase.java`: `list()` usa `findByStatusNotOrderByCreatedAtDesc(STOPPED)`; `sourceOf(workId)` só credita um acompanhamento do MangaDex (ou que divide o lease dele) que tenha ao menos uma versão com `hasChapter()`, em qualquer estado
- [ ] T021 [P] [US2] Em `sourcing/application/SourcingDigest.java`, `lines()` usa `findByStatusNotOrderByCreatedAtDesc(STOPPED)`
- [ ] T022 [P] [US2] Em `sourcing/application/ImportWorkUseCase.java`, trocar `existsBySourceAndExternalId` por `existsBySourceAndExternalIdAndStatusNot(..., STOPPED)` (MangaDex e manga-downloader) e `existsByWorkIdAndSource` por `existsByWorkIdAndSourceAndStatusNot(..., STOPPED)`; apagar de `sourcing/persistence/TrackedWorkRepository.java` os métodos que ficarem sem uso

**Checkpoint**: US1 e US2 verdes juntas.

---

## Phase 5: User Story 3 - Não cruzar com uma rodada em andamento (Priority: P3)

**Goal**: a ação e a rodada nunca se veem pela metade; nenhum download começa depois da ação.

**Independent Test**: ação e registro de versão da mesma obra acompanhada em paralelo, com
transações reais; o resultado é o de uma depois da outra.

### Testes da US3

- [ ] T023 [P] [US3] Em `sourcing-test/application/SourceChapterImporterTest.java`: `shouldSkipVersion_whenTrackedWorkNoLongerActive` (lock devolve obra `STOPPED` ou `PAUSED` → `RegisterChapterUseCase` não é chamado) e `shouldSkipVersion_whenTrackedWorkRemoved` (lock devolve vazio)
- [ ] T024 [P] [US3] Criar `sourcing-test/StopTrackingConcurrencyIntegrationTest.java` no padrão do `RegisterChapterIntegrationTest` (duas transações reais em paralelo, `CountDownLatch`): (a) a rodada segura o lock da obra acompanhada no passo de registro enquanto o ADM chama `stop` → `stop` espera e termina em `STOPPED` com `importedChapters = 1`; (b) `stop` termina antes → o importador pula a versão e nenhum capítulo novo aparece na obra. Nos dois, nenhum capítulo fica sem versão que aponte para ele

### Implementação da US3

- [ ] T025 [US3] Em `sourcing/application/SourceChapterImporter.java`, injetar `TrackedWorkRepository` e trocar `register()` por um passo único numa transação `REQUIRES_NEW` (o `transactionTemplate` que já existe): `trackedWorkRepository.lockById(tracked.getId())`; se vazio ou `!isActive()`, ou se a versão não existe mais, devolve vazio e `run()` sai sem registrar; senão `registerChapter.handle(...)` e `attachChapter` na versão lida dentro da mesma transação. O lock cobre só conferir, inserir e anexar; o download continua fora (ADR-53). Ajustar o construtor em `SourceChapterImporterTest`
- [ ] T026 [US3] Em `sourcing/application/SourceChapterImporter.java`, aplicar a mesma checagem (obra acompanhada ainda `ACTIVE`, sob lock) antes de `replace()` baixar páginas novas, para "baixar de novo" e "usar esta versão" também não começarem depois da ação

**Checkpoint**: as três stories verdes.

---

## Phase 6: Polish & docs de referência

- [ ] T027 [P] Em `docs/ARCHITECTURE.md` §6.4.2, acrescentar `POST /admin/sourcing/tracked/{id}/stop` à lista de ações do ADM, a regra remover × encerrar e que o crédito de `GET /works/{id}/source` depende de capítulo vindo do MangaDex; citar `specs/001-parar-acompanhar-obra/`
- [ ] T028 [P] Em `docs/DATABASE.md` §3, linha da V38 (`STOPPED` em `tracked_work_status`, migration própria)
- [ ] T029 [P] Em `docs/glossario-dominio.md` §5.1, o estado `STOPPED` da obra acompanhada e a definição de "capítulo vindo da fonte"
- [ ] T030 Rodar `./mvnw clean test` (em `backend/`, inclui `ArchitectureTest`) e `npm run build` (em `frontend/`); os dois verdes
- [ ] T031 Seguir o roteiro manual de [quickstart.md](quickstart.md) no profile `local` e marcar o checklist do template de PR

---

## Dependencies & Execution Order

- **Setup (T001)** → **Foundational (T002–T008)** → stories.
- **US1 (T009–T015)** depende só da fase 2. Com a US3, forma o MVP.
- **US2 (T016–T022)** depende da US1 (completa o `stop` criado na T011 e reaproveita endpoint e tela).
- **US3 (T023–T026)** depende só da fase 2 e pode andar em paralelo com a US1 e a US2 (mexe só no importador).
- **Polish (T027–T031)** depois das stories.

Dentro de cada fase, os testes vêm primeiro e precisam falhar antes da implementação.

## Parallel Opportunities

- Fase 2: T002 e T003 juntos; depois T007 e T008 juntos com T006.
- US1: T009, T010, T013 e T014 juntos; T011 → T012 → T015 em sequência.
- US2: T016 e T017 juntos; T020, T021 e T022 juntos depois de T018/T019.
- US3 inteira em paralelo com US1/US2, por outra pessoa.
- Polish: T027, T028 e T029 juntos.

## Implementation Strategy

1. **MVP = Fase 2 + US1 + US3.** Resolve o caso real (#156, SC-002). A US3 entra no MVP
   porque a corrida também existe na remoção: apagar uma obra acompanhada ativa no meio de
   uma rodada criaria capítulo sem versão (research R6).
2. **US2** em seguida, no mesmo PR ou no próximo: fecha o caso com capítulos.
4. Um commit por unidade lógica, mensagem `feat(sourcing): …`, PR para `dev` com a pasta da spec.
