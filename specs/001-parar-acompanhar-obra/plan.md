# Implementation Plan: Parar de acompanhar uma obra

**Branch**: `001-parar-acompanhar-obra` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md) | **Issue**: [#156](https://github.com/lucascatanio/buruna/issues/156)

**Input**: Feature specification from `specs/001-parar-acompanhar-obra/spec.md`

## Summary

O ADM ganha a ação "Parar de acompanhar" numa obra acompanhada. Sem capítulo vindo da
fonte, o acompanhamento e as versões são apagados. Com capítulo, o acompanhamento passa ao
estado novo `STOPPED`: some da lista, do e-mail diário e das rodadas, e continua guardado
para sustentar o crédito da fonte. O importador passa a registrar e anexar o capítulo numa
transação só, sob o lock da obra acompanhada e só se ela ainda estiver ativa, para a ação
nunca cruzar com um download. O crédito "Fonte: MangaDex" passa a depender de haver
capítulo vindo do MangaDex. Decisões: [research.md](research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.9 (frontend)

**Primary Dependencies**: Spring Boot 3.5 (Web, Data JPA, Security), Flyway; React 19, Axios

**Storage**: PostgreSQL 16. Migration V38 só acrescenta `STOPPED` ao enum `tracked_work_status`

**Testing**: JUnit 5 + AssertJ (domínio), Mockito (`SourceChapterImporterTest`), `@SpringBootTest` + Testcontainers (`SourcingIntegrationTest`, `SourcingSyncIntegrationTest`, teste novo de concorrência), ArchUnit

**Target Platform**: Cloud Run (serviço web e Job `buruna-ingest`); no profile `local` a rodada roda na requisição

**Project Type**: web (backend Spring + SPA React)

**Performance Goals**: sem meta nova. A ação lê as versões de uma obra (dezenas a centenas de linhas)

**Constraints**: lock pessimista só no passo final (conferir, inserir, anexar), nunca no download (ADR-53); nada de `@Query` nativa; `sourcing` fala com `work` só por use case público

**Scale/Scope**: poucas dezenas de obras acompanhadas; 1 ADM por vez na prática

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como o plano cumpre | Situação |
|---|---|---|
| I. Clean Architecture | `stop()` no agregado (`sourcing/domain`), a ação em `ManageTrackedWorkUseCase` (`sourcing/application`), consultas derivadas em `sourcing/persistence`, endpoint em `sourcing/web`. Sem pacote `service/` | ✅ |
| II. Fronteiras | Nenhum import de `work.domain`/`work.persistence`. Remoção de capítulo não acontece (FR-005). Consultas derivadas, sem SQL nativo nem JOIN com `chapters` | ✅ |
| III. Domínio rico | Transição e terminalidade de `STOPPED` como invariante do `TrackedWork`; regra "capítulo vindo da fonte" num método de `SourceChapter` (`hasChapter()`); exceções de domínio existentes (`InvalidSourceActionException`, `TrackedWorkNotFoundException`) | ✅ |
| IV. Segurança | Endpoint sob o `@PreAuthorize("hasRole('ADMIN')")` da classe; teste de 403 para READER | ✅ |
| V. Testes | Domínio (`TrackedWorkTest`), importador (`SourceChapterImporterTest`), integração dos dois caminhos, reimport, crédito, e-mail diário; concorrência com transações reais em paralelo | ✅ |
| VI. Simplicidade | Sem coluna nova (só o valor do enum), sem endpoint de pré-visualização, `confirm()` como o resto da tela | ✅ |
| VII. Trabalho longo / consistência | Ação sob `lockById`; importador confere estado sob o mesmo lock antes de registrar; download fora do lock; transações próprias (`REQUIRES_NEW`) como hoje | ✅ |
| Fluxo | Spec com issue, docs de referência atualizados no mesmo PR (ARCHITECTURE §6.4.2, DATABASE V38, glossário) | ✅ |

**Re-check pós-design**: sem violação. *Complexity Tracking* vazio.

## Project Structure

### Documentation (this feature)

```text
specs/001-parar-acompanhar-obra/
├── spec.md
├── plan.md              # este arquivo
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── admin-sourcing.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/buruna/sourcing/
├── domain/
│   ├── TrackedWork.java               # stop(), isStopped(), guardas de terminalidade
│   ├── TrackedWorkStatus.java         # + STOPPED
│   └── SourceChapter.java             # hasChapter()
├── application/
│   ├── StopTrackingResult.java        # novo DTO
│   ├── ManageTrackedWorkUseCase.java  # stop(): remove ou encerra, sob lock; lock()/find() tratam STOPPED como não encontrada
│   ├── SourceChapterImporter.java     # registrar + anexar sob lock, só se ACTIVE
│   ├── ListTrackedWorksUseCase.java   # lista sem STOPPED; crédito por capítulo vindo da fonte
│   ├── TrackedWorkResponse.java       # + importedChapters
│   ├── SourcingDigest.java            # e-mail sem STOPPED
│   └── ImportWorkUseCase.java         # "já acompanhada" ignora STOPPED
├── persistence/
│   └── TrackedWorkRepository.java     # consultas derivadas novas
└── web/
    └── AdminSourcingController.java   # POST /tracked/{id}/stop

backend/src/main/resources/db/migration/V38__tracked_work_stopped.sql

backend/src/test/java/com/buruna/sourcing/
├── domain/TrackedWorkTest.java
├── application/SourceChapterImporterTest.java
├── SourcingIntegrationTest.java
├── SourcingSyncIntegrationTest.java
└── StopTrackingConcurrencyIntegrationTest.java   # novo

frontend/src/
├── api/sourcingApi.ts                 # stopTrackingWork()
├── types/sourcing.ts                  # importedChapters, StopTrackingResult
└── components/sourcing/TrackedWorkItem.tsx  # botão + confirm()

docs/ARCHITECTURE.md   # §6.4.2: ação nova e regra do crédito
docs/DATABASE.md       # V38
docs/glossario-dominio.md  # estado STOPPED
```

**Structure Decision**: web app existente (`backend/` + `frontend/`). Tudo no contexto
`sourcing`. A ação entra como método do `ManageTrackedWorkUseCase`, que já reúne as ações do
ADM numa obra acompanhada e já tem o repositório de versões; um use case próprio seria uma
classe a mais sem ganho.

## Complexity Tracking

Sem violações a justificar.
