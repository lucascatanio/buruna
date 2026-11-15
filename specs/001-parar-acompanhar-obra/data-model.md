# Data Model: Parar de acompanhar uma obra

Só o contexto `sourcing` muda. Nenhuma tabela de `work` é tocada.

## TrackedWork (agregado, `tracked_works`)

Campo alterado:

| Campo | Antes | Depois |
|---|---|---|
| `status` (`tracked_work_status`) | `ACTIVE`, `PAUSED` | `ACTIVE`, `PAUSED`, `STOPPED` |

Migration **V38__tracked_work_stopped.sql**: `ALTER TYPE tracked_work_status ADD VALUE
'STOPPED';`, sozinha (o valor novo não pode ser usado na transação que o cria).

### Estados

```text
           pause()              stop()
  ACTIVE ───────────► PAUSED ──────────► STOPPED (terminal)
    ▲  │                │
    │  └── resume() ◄───┘
    │
    └─────────────── stop() ───────────► STOPPED
```

- `stop()`: de `ACTIVE` ou `PAUSED` para `STOPPED`. Limpa `listingRequested`. De `STOPPED`
  lança `InvalidSourceActionException`.
- `STOPPED` é terminal: `pause`, `resume`, `requestListing`, `trackLanguage`, `ignoreGroup`
  e `stopIgnoringGroup` lançam `InvalidSourceActionException`.
- `isActive()` continua `status == ACTIVE`. Novo `isStopped()`.

### Remoção

Sem capítulo vindo da fonte (nenhuma versão com `chapterId`), o use case apaga as versões
(`source_chapters`) e o `TrackedWork`. Idiomas e grupos ignorados saem com o agregado
(coleções do próprio `TrackedWork`; as FKs já têm `ON DELETE CASCADE`).

## SourceChapter (agregado, `source_chapters`)

Sem campo novo. Regra usada pela feature:

- **Capítulo vindo da fonte** = versão com `chapterId != null` (ver
  [research R1](research.md#r1-o-que-conta-como-capítulo-vindo-da-fonte)).

## TrackedWorkResponse (DTO da application)

Campo novo: `importedChapters` (`long`): versões da obra acompanhada com `chapterId`
preenchido. É o número que a confirmação mostra.

## StopTrackingResult (DTO da application, novo)

| Campo | Tipo | Valores |
|---|---|---|
| `outcome` | `String` | `REMOVED` (apagado) ou `STOPPED` (encerrado) |
| `importedChapters` | `long` | capítulos vindos da fonte no momento da ação |

## Consultas novas em `TrackedWorkRepository`

Todas derivadas pelo Spring Data, sem `@Query`:

- `findByStatusNotOrderByCreatedAtDesc(TrackedWorkStatus status)`: lista e e-mail diário.
- `existsBySourceAndExternalIdAndStatusNot(SourceName, String, TrackedWorkStatus)`: import.
- `existsByWorkIdAndSourceAndStatusNot(UUID, SourceName, TrackedWorkStatus)`: import.

`findAllByOrderByCreatedAtDesc`, `existsBySourceAndExternalId` e `existsByWorkIdAndSource`
saem se ficarem sem uso.
