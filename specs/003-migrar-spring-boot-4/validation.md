# Validação: Migrar o backend para o Spring Boot 4

**Data**: 2026-11-21 | **Commit**: e2da71f | **Branch**: chore/124-spring-boot-4

**Resultado**: APROVADA

## Checagens automáticas

| Checagem | Resultado | Detalhe |
|---|---|---|
| `./mvnw clean test` | ✅ | 756 testes, 0 falhas, 0 erros, 0 pulados (BUILD SUCCESS, 2:31 min). No Boot 3.5.16 eram 754; os 2 a mais são `MangaDownloaderClientTest#shouldReadVolumeAsZero_whenCliPrintsNullVolume` e `ExternalSourceConfigTest` |
| `npm run build` | — não se aplica | O frontend não muda (FR-014): `git diff --stat origin/dev -- frontend/` sai vazio |

## Varredura (V1–V4)

Imagem `buruna-backend:boot4` (target `service`, JVM 21.0.13.1), `aquasec/trivy:latest`,
`--severity CRITICAL,HIGH`. A varredura rodou de novo depois do rebase na `dev`, que trouxe o
`manga-downloader` 25.2.0 (#162), e deu o mesmo resultado.

O roteiro rodou em `c482980`, antes do rebase. O rebase só trouxe a troca de tag da imagem do
`manga-downloader` no `backend/Dockerfile` (#162, já validada no próprio PR). Os commits da
feature não mudaram.

| # | Passo | Esperado | Observado | Resultado |
|---|---|---|---|---|
| V1 | Trivy na imagem | Nenhuma CRITICAL | 0 CRITICAL | ✅ |
| V2 | CVE-2026-47884 e CVE-2026-47890 na saída | Não aparecem | Não aparecem | ✅ |
| V3 | Bloco de sobrescritas no `pom.xml` | Só `pdfbox.version` e `jbig2-imageio.version` | Só as duas (mais `java.version`) | ✅ |
| V4 | HIGH que sobram | Lista registrada | CVE-2026-102554 em `com.google.guava:guava` 33.6.0-jre (corrigido em 33.7.2; transitivo do `google-cloud-storage`) e CVE-2026-78661 em `golang.org/x/net` v0.61.0 (corrigido em 0.62.0; binário do `manga-downloader`, também na imagem 25.2.0). Nenhum vem do Spring | ✅ |

## Roteiro do quickstart

App local com `scripts/dev-env.sh` (profile `local`), o container `buruna_postgres` reaproveitado
(`COMPOSE_PROJECT_NAME=buruna`) e a CLI do manga-downloader tirada da imagem 25.1.0.

| # | Passo | Esperado | Observado | Resultado |
|---|---|---|---|---|
| 1 | Subir o app e ler `.dev-env/backend.log` | Sobe sem erro; Flyway em dia, sem migration nova | `Spring Boot (v4.1.3)`, `Successfully validated 37 migrations`, `Schema "public" is up to date. No migration necessary.`, `Started BurunaApplication in 9.49 seconds` | ✅ |
| 2 | `GET /api/health` | `{"status":"UP"}` | 200 `{"status":"UP"}` | ✅ |
| 3 | Entrar como ADMIN | Login funciona e a home carrega | Login pela tela leva a `/biblioteca`, com a lista de obras e o menu Admin | ✅ |
| 4 | Recarregar a página | Sessão renovada pelo cookie, sem pedir login | O reload fica em `/biblioteca`; `POST /api/auth/refresh` responde 200 com `accessToken` novo | ✅ |
| 5 | Conferir o cookie de refresh | `HttpOnly`, `SameSite=Strict`, mesmo nome e caminho | `document.cookie` não enxerga `buruna_refresh` (HttpOnly). O `Set-Cookie` do login é idêntico ao da baseline: `buruna_refresh=…; Path=/api/auth; Max-Age=604800; HttpOnly; SameSite=Strict` | ✅ |
| 6 | Enviar capítulo em PDF, CBZ e um EPUB | Os três aceitos, processados e listados | Pela tela: Cap. 4 (CBZ, 2 páginas), Cap. 5 (PDF escaneado, 2 páginas) e as edições EPUB "Validação, 2026" e "Validação completa, 2026" listadas | ✅ |
| 7 | Enviar `.pdf` que não é PDF | Mesma recusa e mensagem de hoje | Cap. 6 aparece desabilitado com "O arquivo não é um PDF válido" e a opção "Processar de novo" (mensagem do domínio, coberta pelo `ChapterUploadIntegrationTest`) | ✅ |
| 8 | Abrir cada capítulo no leitor e avançar | Páginas carregam e o progresso é salvo | CBZ e PDF: imagem 600x900 carregada, "1 / 2" → "2 / 2"; `POST …/progress {"currentPage":2}` → 200, e o `GET` devolve `currentPage: 2, finished: true`. EPUB: abre em "Capítulo 1, 25%", avança a "Capítulo 2, 75%", e o reload volta a "Capítulo 2, 75%". O capítulo de páginas terminado reabre na página 1 porque o frontend só retoma capítulo não terminado (`ReaderPage.tsx:129`), sem mudança nesta feature | ✅ |
| 9 | Avaliar a obra e pôr na lista | Funciona como antes | "Dar nota 4": `POST /api/works/{id}/rating` → 201. "Quero ler": `PUT /api/reading-list/{id}` → 200 | ✅ |
| 10 | Importar série curta do MangaDex em `/admin/import` | Capítulos baixados e publicados | Busca no AniList achou a obra; "Listar" mostrou "10 capítulos · vi, pt-br, en · 4 grupos"; com só pt-br marcado, os caps. 26, 27 e 28 ficaram PUBLISHED (26, 27 e 28 páginas) e a obra aparece em "Obras acompanhadas" com "3 baixados" | ✅ |
| 11 | `POST /api/admin/sourcing/run` duas vezes | Nenhum capítulo duplicado | 202 nas duas; 8 capítulos antes e depois; 0 grupos (obra, idioma, número, grupo) duplicados | ✅ |
| 12 | `POST /api/admin/jobs/inactivity` com e sem `X-Job-Secret` | 200 com o resumo; sem o header, a recusa de hoje | Com o segredo: 200 "Job executado". Sem o header: 400 `ErrorResponse` "Required header 'X-Job-Secret' is not present.", o mesmo que o `InactivityJobIntegrationTest#triggerInactivity_missingSecretHeader_returns400` exigia no Boot 3.5 | ✅ |
| 13 | `POST /api/admin/jobs/storage-orphans` com o segredo | 200 com o resumo, sem apagar arquivo em uso | 200 `{"analyzed":0,"orphans":0,"deleted":0,"dryRun":false,"aborted":false}` | ✅ |
| 14 | `GET /api/works/<uuid-inexistente>` com token | 404 no formato `ErrorResponse` | 404 `{"status":404,"error":"Not Found","message":"Mangá não encontrado com slug: …","path":"…","timestamp":"…"}` | ✅ |
| 15 | `curl -i /api/works` sem token | 401 igual à baseline | Idêntico à [baseline](baseline-boot3.md) fora `Date`: 401, mesmos cabeçalhos de segurança, corpo vazio | ✅ |
| 15b | `curl -i /api/nao-existe` com token | Igual à baseline | Idêntico fora `Date`/`timestamp`: 404 com `"message":"No static resource nao-existe."` | ✅ |
| 15c | Preflight CORS com a origem do front e com `evil.invalid` | `Access-Control-*` iguais à baseline | Idêntico: 200 com `Access-Control-Allow-Origin: http://localhost:5173` e credentials; 403 "Invalid CORS request" para a outra origem | ✅ |
| 16 | Cadastrar conta e aprovar como ADMIN | A conta entra depois da aprovação | Cadastro pela tela; login antes da aprovação recusado (403, "Your account is pending approval"); aprovada em "Aprovações pendentes"; login depois → 200 | ✅ |
| 17 | Pedir redefinição de senha da conta nova | E-mail no log e o link redefine a senha | Com `RESEND_API_KEY` vazia: `POST /auth/password/forgot` → 200 e `[EMAIL SKIP] … Subject: [Burūna] Password reset` no log. O link usa um token conhecido gravado como `sha256Hex` no registro criado pelo pedido (o log não traz o link): `reset-info` → 200, a tela "Redefinir senha" troca a senha, a antiga dá 401 e a nova 200 | ✅ |
| 18 | Console do navegador no roteiro | Nenhum erro de rede ou parse de JSON vindo da migração | Nenhum erro de parse. Erros de rede, todos esperados ou de ambiente: 401 em `/auth/refresh` sem sessão (5), 403 no login da conta pendente, 404 em 9 capas cujos arquivos não existem no storage local (dados de 2026-10-17), 500 no "esqueci a senha" com a chave de exemplo do Resend (ver observações) | ✅ |
| P1–P10 | Seção "depois do deploy" | — | Feita em produção depois do deploy; ver a seção abaixo | — não se aplica (local) |

## Falhas

Nenhuma.

## Não executados

Nenhum. A seção "depois do deploy" (P1–P10) não se aplica ao ambiente local; o resultado em produção está abaixo.

## Observações (fora do escopo da migração)

- **Chave de exemplo do Resend no `.env` local.** Com `RESEND_API_KEY=SUA_…`, o backend chama
  `api.resend.com` e recebe `401 "API key is invalid"`. O "esqueci a senha" vira 500 e o aviso de
  aprovação só registra um WARN. Isso prova que o cliente HTTP migrado chega ao Resend e lê a
  resposta. O Boot 3.5 com o mesmo `.env` falharia igual. O passo 17 do quickstart diz que o
  e-mail "sai no log" no profile local, mas isso só vale com a chave vazia, e o link nunca aparece
  no log. O texto do passo precisa de ajuste.
- **Texto de UI em inglês.** "Your account is pending approval" vem do `AuthenticationService`
  (commit `1200761`, anterior a esta feature).
- **Amostra de EPUB.** O EPUB mínimo dos testes (`<package/>` vazio) passa na validação do backend,
  mas o leitor não o monta ("Erro ao abrir o EPUB"). Com um EPUB 3 válido o leitor abre sem erro.
- **Avisos de obsolescência novos no build**, aceitos pelo research R6: `HttpStatus.UNPROCESSABLE_ENTITY`
  (`GlobalExceptionHandler`), `org.springframework.lang.NonNull` (`JwtFilter`, `ProxySecretFilter`) e
  `HV000271` (`@Valid` na `List` de `ImportWorkRequest#languages`).

## Depois do deploy (produção, v2.3.1)

Deploy de 2026-11-21: release #172, merge `e7387d7` na `main`, revisão `buruna-backend-00053-6kp` com
100% do tráfego. Para voltar: revisão `buruna-backend-00052-kpc` e Job em
`buruna-ingest@sha256:32ad5f1ef54f920bb9ffff5c5d3ffd95a2a5f1a3c8ecc6efb70ec8d923774b03`.

| # | Passo | Observado | Resultado |
|---|---|---|---|
| P1 | Aba logada antes do deploy, aberta depois | Entrou direto, sem pedir login; `POST /api/auth/refresh` → 200 | ✅ |
| P2 | Log de startup da revisão nova | `Spring Boot (v4.1.3)`, `Successfully validated 37 migrations`, `Schema "public" is up to date. No migration necessary.`; `/api/health` → 200 | ✅ |
| P3 | Login e leitura | Attack on Titan aberto no leitor, 2 páginas avançadas, `POST …/progress` → 200, console sem erro. Nenhum livro do catálogo foi aberto | ✅ |
| P4 | Envio de capítulo curto | Capítulo CBZ numa obra privada (`POST …/chapters/finalize` → 201). O Job `buruna-ingest` rodou com Spring Boot v4.1.3 e saiu com `exit(0)`, e o capítulo abriu no leitor ("1 / 2") | ✅ |
| P5 | Import | Não foi criada obra pública: o passo usou "Verificar agora" no Jujutsu Kaisen (`POST …/check` → 202). Log do Job: "275 versões na fonte, 0 novas", `exit(0)`, sem erro de JSON | ✅ |
| P6 | Os três jobs do Cloud Scheduler, rodados pelo mantenedor | `buruna-inactivity` → 200, `buruna-storage-orphans` → 200, `buruna-sourcing-sync` → execução `buruna-ingest-jq56j` com `exit(0)` | ✅ |
| P7 | "Esqueci minha senha" com conta real | `POST /auth/password/forgot` → 200, push do Pub/Sub em `/internal/pubsub/password-reset` → 204, `ResendEmailSender: Email sent successfully`, `POST /auth/password/reset` → 200. O mantenedor confirmou que o e-mail chegou e que a senha nova entra | ✅ |
| P8 | Trivy na imagem publicada (`buruna-backend@sha256:3d23521e…`) | 0 CRITICAL, sem CVE-2026-47884/47890; só os 2 HIGH conhecidos (#165, #166) | ✅ |
| P9 | Próxima execução agendada da `buruna-sourcing-sync` | Zero ERROR no serviço e no Job desde o deploy. A execução vista é a manual do P6; as agendadas (a cada 3 h) entram na checagem do P10 | ⏳ |
| P10 | Uma semana depois (2026-11-28) | Pendente (T036) | ⏳ |
