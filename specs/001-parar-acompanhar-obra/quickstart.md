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

## Roteiro no navegador (profile `local`)

Executado pelo `/validar-quickstart`, que sobe o app com `scripts/dev-env.sh up` e entra com
a conta de `scripts/dev-env.sh admin`. O import do MangaDex é de verdade: use um oneshot.

| # | Ação | Resultado esperado | Cobre |
|---|---|---|---|
| 1 | Entrar como ADMIN e abrir `/admin/import` | A lista de obras acompanhadas aparece, cada uma com "Parar de acompanhar" | FR-001, FR-010 |
| 2 | Importar um oneshot do MangaDex num idioma que ele não tem e pausar | A obra aparece pausada, com 0 capítulos | preparação |
| 3 | "Parar de acompanhar" nessa obra | A confirmação diz que nenhum capítulo veio da fonte e que o acompanhamento será removido | FR-002, FR-014 |
| 4 | Cancelar a confirmação | Nada muda na lista | US1 cenário 3 |
| 5 | Repetir e confirmar | A obra some da lista; `GET /api/works/{workId}/source` responde 204 | FR-003, FR-006, FR-011 |
| 6 | Importar de novo a mesma série | Aceita, sem 409 | FR-009 |
| 7 | Importar um oneshot com capítulo no idioma e esperar a publicação | O capítulo aparece na página da obra | preparação |
| 8 | "Parar de acompanhar" nessa obra | A confirmação diz "1 capítulo veio desta fonte e continua no ar" e que a fonte continua creditada | FR-002, FR-014 |
| 9 | Confirmar | A obra some da lista e dos avisos | FR-003, FR-012, US2 cenário 4 |
| 10 | Abrir a obra como leitor e o capítulo no leitor | O capítulo continua listado e abre | FR-005, US2 cenário 2 |
| 11 | `GET /api/works/{workId}/source` | 200 com o MangaDex | FR-006, US2 cenário 3 |
| 12 | `POST /api/admin/sourcing/run` e o log de `.dev-env/backend.log` | Nenhuma listagem nem download dessa obra | FR-004, SC-004 |
| 13 | `POST /api/admin/sourcing/tracked/{id}/stop` de novo na obra do passo 9 | 404; na tela, "Esta obra não está mais acompanhada." | edge case de dois ADMs |

## Depois do deploy (SC-002)

Em produção, pare de acompanhar o Jujutsu Kaisen do MangaDex e confira que a entrada do
mangafire.to continua na lista e recebendo capítulos no próximo sync.
