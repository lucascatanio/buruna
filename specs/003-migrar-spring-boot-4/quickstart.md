# Quickstart: validar a migração para o Spring Boot 4

Setup local: [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md). O que precisa ficar idêntico:
[contracts/api-invariants.md](contracts/api-invariants.md). Decisões: [research.md](research.md).

## Automatizado

```bash
cd backend && ./mvnw clean test
```

O `npm run build` não entra, porque o frontend não é tocado (FR-014). Se o diff do PR
mexer em `frontend/`, o roteiro falha.

| Cenário | Spec | Onde |
|---|---|---|
| Suíte inteira verde, com pelo menos o mesmo número de testes de antes (anotar o total do Boot 3.5 no commit 1) | FR-013, SC-002 | saída do `./mvnw clean test` |
| Fronteiras entre contextos | FR-013 | `ArchitectureTest` |
| `LoginResponse` sem campos nulos | FR-009 | `IdentityIntegrationTest` (caracterização, R10) |
| Data em ISO-8601 com fuso; campo nulo sem `@JsonInclude` aparece como `null` | FR-009 | `ChapterReadingIntegrationTest` (caracterização) |
| `ErrorResponse` com os cinco campos num 404 | FR-009, US1 cenário 5 | `WorkIntegrationTest` (caracterização) |
| Corpo do 401, rota inexistente, `/health` | FR-009, FR-011 | comparação com [baseline-boot3.md](baseline-boot3.md), passos 2, 15, 15b e 15c abaixo |
| Listagem do manga-downloader com `volume` nulo | FR-007 | `MangaDownloaderClientTest` |
| Login, refresh, 2FA, reset de senha, aprovação | FR-004, US1, US2 cenários 4–5 | `IdentityIntegrationTest` |
| Leitura, progresso, avaliações, lista | FR-005, US1 cenário 3 | `ReadingIntegrationTest`, `ChapterReadingIntegrationTest`, `EngagementIntegrationTest` |
| Envio e recusa de arquivo hostil | FR-006, US2 cenários 1–2 | `ChapterUploadIntegrationTest` e testes de `shared/media` |
| Import e sincronização, idempotência | FR-007, US2 cenário 3, US3 cenário 3 | `SourcingIntegrationTest`, `SourcingSyncIntegrationTest` |
| Jobs com e sem `X-Job-Secret` | FR-008, US3 cenários 1–2 | `InactivityJobIntegrationTest` |
| Clientes HTTP externos desserializam com o Jackson 3 | FR-007, FR-008 | `MangaDexSourceTest`, `AniListSourceTest`, `CaptchaServiceTest`, `PubSubPublisherTest`, `CloudRunJobLauncherTest`, `ResendEmailSenderTest` |
| Matriz de acesso | FR-010, US1 cenário 4 | testes de integração de cada contexto |

## Varredura de vulnerabilidades (local, antes do PR)

O Trivy não está no CI nem instalado na máquina, então roda pela imagem oficial:

```bash
docker build --target service -t buruna-backend:boot4 backend
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest \
  image --severity CRITICAL,HIGH buruna-backend:boot4
```

| # | Ação | Resultado esperado | Cobre |
|---|---|---|---|
| V1 | Rodar o comando acima | Nenhuma linha CRITICAL | FR-002, SC-001 |
| V2 | Procurar `CVE-2026-47884` e `CVE-2026-47890` na saída | Não aparecem | FR-001, SC-001 |
| V3 | Conferir no `pom.xml` o bloco de sobrescritas | Só `pdfbox.version` e `jbig2-imageio.version`. Qualquer outra sobrescrita tem o motivo ao lado | FR-003 |
| V4 | Anotar as HIGH que sobrarem no `validation.md` | Lista registrada (não bloqueia, mas vira issue se tiver correção) | FR-002 |

## Roteiro no navegador (profile `local`)

Executado pelo `/validar-quickstart`, que sobe o app com `scripts/dev-env.sh up` e entra com
a conta de `scripts/dev-env.sh admin`. Para o import, use uma série curta do MangaDex.

| # | Ação | Resultado esperado | Cobre |
|---|---|---|---|
| 1 | Subir o app e ver o log de `.dev-env/backend.log` | Sobe sem erro. O Flyway informa que o esquema está em dia, sem migration nova | FR-012, R4 |
| 2 | `GET /api/health` | `{"status":"UP"}` | FR-011 |
| 3 | Entrar como ADMIN | O login funciona e a home carrega | FR-004, US1 cenário 2 |
| 4 | Recarregar a página (o access token vive só em memória e se perde no reload) | A sessão é renovada pelo cookie, sem pedir login | FR-004, US1 cenário 2 |
| 5 | Nas DevTools, conferir o cookie de refresh | `HttpOnly`, `SameSite=Strict`, mesmo nome e caminho de antes | FR-011 |
| 6 | Criar uma obra e enviar um capítulo em PDF, um em imagens (CBZ) e um EPUB | Os três são aceitos, processados e listados | FR-006, US2 cenário 1 |
| 7 | Enviar um arquivo com extensão `.pdf` que não é PDF | Mesma recusa e mesma mensagem de hoje | FR-006, US2 cenário 2 |
| 8 | Abrir cada capítulo no leitor e avançar páginas | As páginas carregam e o progresso é salvo (reabrir volta à página) | FR-005, US1 cenário 3 |
| 9 | Avaliar a obra e adicioná-la à lista de leitura | Funciona como antes | FR-005 |
| 10 | Em `/admin/import`, importar uma série curta do MangaDex | Os capítulos são baixados e publicados | FR-007, US2 cenário 3 |
| 11 | `POST /api/admin/sourcing/run` duas vezes seguidas | Nenhum capítulo duplicado | FR-007, US3 cenário 3 |
| 12 | `POST /api/admin/jobs/inactivity` com `X-Job-Secret: dev-secret`, e de novo sem o header | 200 com o resumo; sem o header, a mesma recusa de hoje | FR-008, US3 cenários 1–2 |
| 13 | `POST /api/admin/jobs/storage-orphans` com o segredo | 200 com o resumo, sem apagar arquivo em uso | FR-008, US3 cenário 1 |
| 14 | `GET /api/works/<uuid-inexistente>` com token | 404 no formato `ErrorResponse` | FR-009, US1 cenário 5 |
| 15 | `curl -s -i localhost:8080/api/works` (sem token) | 401 com status, cabeçalhos de segurança e corpo iguais aos da [baseline](baseline-boot3.md). Só `timestamp` difere | FR-009, FR-010, FR-011 |
| 15b | `curl -s -i localhost:8080/api/nao-existe` com token | Mesmo status e mesmo corpo da baseline | FR-009 |
| 15c | Preflight `OPTIONS /api/works` com `Origin: http://localhost:5173`, e de novo com `Origin: http://evil.invalid` | Cabeçalhos `Access-Control-*` iguais aos da baseline: liberado para a origem do front, recusado para a outra | FR-011 |
| 16 | Cadastrar uma conta nova e aprová-la como ADMIN | A conta entra depois da aprovação | FR-004, US2 cenário 4 |
| 17 | Pedir redefinição de senha da conta nova | No profile `local`, o e-mail sai no log e o link redefine a senha | FR-004, US2 cenário 5 |
| 18 | Abrir o console do navegador durante o roteiro | Nenhum erro de rede ou de parse de JSON | FR-009, FR-014 |

## Depois do deploy (produção)

Executado à mão depois do release. As mutações do `gcloud` ficam com o usuário.

| # | Ação | Resultado esperado | Cobre |
|---|---|---|---|
| P1 | Antes do deploy, ficar logado no site. Depois do deploy, voltar à mesma aba | Continua logado, sem pedir login | FR-004, SC-004 |
| P2 | Log de startup da revisão nova | Sobe sem erro. O Flyway não aplica nada | FR-012 |
| P3 | Login, abrir um capítulo de cada formato, avançar páginas | Funciona na primeira tentativa | FR-004, FR-005, SC-003 |
| P4 | Enviar um capítulo curto | O Job `buruna-ingest` roda na imagem nova e o capítulo aparece | FR-006, SC-003 |
| P5 | Importar uma série curta | Capítulos publicados | FR-007, SC-003 |
| P6 | `gcloud scheduler jobs run buruna-inactivity`, `buruna-storage-orphans` e `buruna-sourcing-sync` (usuário roda), e conferir os logs | Os três concluem com sucesso | FR-008, US3, SC-003 |
| P7 | "Esqueci a senha" com uma conta real | O e-mail chega pelo Pub/Sub e o link funciona | FR-004, FR-008 |
| P8 | Trivy na imagem publicada no Artifact Registry | Zero CRITICAL | FR-002, SC-001 |
| P9 | Na próxima execução agendada da `buruna-sourcing-sync` (até 3 h) | Rodou sem erro no log | FR-008, US3 |
| P10 | Uma semana depois, conferir os erros do Cloud Run e o canal de feedback | Nenhum erro novo atribuível à migração | SC-005 |

**Volta, se P2–P7 falhar**: mandar 100% do tráfego para a revisão anterior
(`gcloud run services update-traffic buruna-backend --region us-east1 --to-revisions <anterior>=100`)
e apontar o Job para a imagem anterior
(`gcloud run jobs update buruna-ingest --region us-east1 --image <imagem anterior>`). A revisão
e a imagem são anotadas antes do deploy (tasks T035). O banco não muda. Meta: menos de 5 minutos (SC-006).
