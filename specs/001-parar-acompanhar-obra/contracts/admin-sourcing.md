# Contratos HTTP: Parar de acompanhar uma obra

Base: `/api`. Erros seguem o `ErrorResponse {status, error, message, path, timestamp}`.

## Novo: `POST /admin/sourcing/tracked/{id}/stop`

Para de acompanhar a obra acompanhada `{id}`. Só `ADMIN` (`@PreAuthorize` da classe).

**Resposta 200**

```json
{ "outcome": "REMOVED", "importedChapters": 0 }
```

```json
{ "outcome": "STOPPED", "importedChapters": 12 }
```

| Status | Quando |
|---|---|
| 200 | Acompanhamento apagado (`REMOVED`, nenhum capítulo veio da fonte) ou encerrado (`STOPPED`) |
| 403 | Papel diferente de `ADMIN` |
| 404 | Obra acompanhada não existe, já foi apagada ou já está encerrada (`TrackedWorkNotFoundException`, a mesma das outras ações). O front mostra "Esta obra não está mais acompanhada." e recarrega a lista |

## Alterado: `GET /admin/sourcing/tracked`

- Não traz mais obras acompanhadas encerradas.
- Cada item ganha `importedChapters` (número): versões com capítulo na obra.

```json
{
  "id": "…", "workTitle": "Jujutsu Kaisen", "source": "MANGADEX", "status": "PAUSED",
  "chapterCounts": { "PENDING": 0, "READY": 0, "FAILED": 0, "IGNORED": 0 },
  "importedChapters": 0
}
```

## Alterado: ações numa obra encerrada

`POST /admin/sourcing/tracked/{id}/check|pause|resume|languages|ignored-groups`,
`DELETE /admin/sourcing/tracked/{id}/ignored-groups/{groupId}`,
`GET /admin/sourcing/tracked/{id}/alerts|groups|source-languages` e
`POST /admin/sourcing/source-chapters/{id}/retry|redownload|use` de uma versão de obra
encerrada respondem **404**, como para uma obra que não existe.

## Alterado: `POST /admin/sourcing/imports`

Uma obra acompanhada encerrada não conta como "já acompanhada": importar a mesma série na
mesma fonte (ou vincular a mesma obra ao MangaDex) cria um acompanhamento novo, em vez do
409 de hoje.

## Alterado: `GET /works/{workId}/source` (público)

- **200** `{ "name": "MangaDex", "url": "…" }` só quando algum acompanhamento do MangaDex
  da obra (ativo, pausado ou encerrado) tem ao menos uma versão com capítulo.
- **204** caso contrário, inclusive com acompanhamento do MangaDex que não trouxe capítulo
  (hoje responde 200 nesse caso).
