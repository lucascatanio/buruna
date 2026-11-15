# Quickstart: validar "Parar de acompanhar"

Setup local: [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md). Contratos:
[contracts/admin-sourcing.md](contracts/admin-sourcing.md).

## Automatizado

```bash
cd backend && ./mvnw clean test
cd frontend && npm run build
```

Cobertura que precisa existir e passar (nomes em [tasks.md](tasks.md)):

| Cenário | Spec | Onde |
|---|---|---|
| `stop()` de ACTIVE e de PAUSED; STOPPED é terminal | FR-011, FR-012 | `TrackedWorkTest` |
| Sem capítulo vindo da fonte: remove acompanhamento e versões | FR-011, Story 1 | `SourcingIntegrationTest` |
| Com capítulo: encerra, capítulo continua no ar, crédito continua | FR-005, FR-006, FR-012, Story 2 | `SourcingIntegrationTest` |
| Encerrada some da lista, do e-mail diário e das rodadas | FR-003, FR-004 | `SourcingIntegrationTest`, `SourcingSyncIntegrationTest` |
| Reimportar a mesma série depois de remover e de encerrar | FR-009, FR-013 | `SourcingIntegrationTest` |
| Crédito some quando o MangaDex nunca trouxe capítulo | FR-006 | `SourcingIntegrationTest` |
| Ação e registro de versão concorrentes não deixam capítulo sem vínculo | FR-007, Story 3 | `StopTrackingConcurrencyIntegrationTest` |
| Importador pula versão de obra que não está mais ativa | FR-004, FR-007 | `SourceChapterImporterTest` |
| Só ADMIN | FR-010 | `SourcingIntegrationTest` |

## Manual (profile `local`)

1. Suba backend e frontend (DEVELOPMENT.md) e entre como ADMIN em `/admin/import`.
2. Importe uma obra do MangaDex num idioma que ela não tem (nenhum capítulo entra). Pause.
3. **Parar de acompanhar** → a confirmação diz que nenhum capítulo veio da fonte e que o
   acompanhamento será removido. Confirme: a obra some da lista. `GET
   /api/works/{workId}/source` responde 204.
4. Importe de novo a mesma série: aceita, sem 409.
5. Importe uma obra com capítulos e espere a rodada publicar alguns.
6. **Parar de acompanhar** → a confirmação diz "N capítulos vieram desta fonte e continuam
   no ar". Confirme: a obra some da lista; os capítulos continuam na página da obra e
   abrem no leitor; `GET /api/works/{workId}/source` continua com o MangaDex.
7. `POST /api/admin/sourcing/run`: nenhuma listagem nem download dessa obra no log.

## Depois do deploy (SC-002)

Em produção, pare de acompanhar o Jujutsu Kaisen do MangaDex e confira que a entrada do
mangafire.to continua na lista e recebendo capítulos no próximo sync.
