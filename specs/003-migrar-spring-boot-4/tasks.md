---
description: "Tarefas da migração do backend para o Spring Boot 4"
---

# Tasks: Migrar o backend para o Spring Boot 4

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/api-invariants.md](contracts/api-invariants.md),
[quickstart.md](quickstart.md)

**Issue**: [#124](https://github.com/lucascatanio/buruna/issues/124)

**Tests**: entram por exigência da constituição (princípio V, rede de segurança antes de
refatorar) e do FR-013. São asserções novas em testes que já existem, escritas e verdes
**ainda no Boot 3.5** (research R10).

**Organização**: a troca de versão é atômica, porque o código não compila pela metade entre
as duas linhas. Por isso a migração toda fica na fase Foundational. As fases de cada user
story conferem que aquela jornada continua igual e corrigem o que não estiver.

Todos os caminhos são relativos à raiz da worktree `/home/lucascatanio/Projects/buruna-wt-boot4`
(branch `chore/124-spring-boot-4`). Os comandos Maven rodam dentro de `backend/`, com o
Docker ligado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: US1, US2 ou US3 da spec

---

## Phase 1: Setup (régua do Boot 3.5)

**Purpose**: deixar registrado como o sistema se comporta hoje, antes de qualquer mudança.

- [X] T001 Fazer o commit da pasta da spec sem código: `git add specs/003-migrar-spring-boot-4 && git commit -m "docs(specs): spec, plano e tarefas da migração para o Boot 4 (#124)"`. Rodar depois do `/speckit-analyze` limpo. Sem rodapé de co-autoria de IA
- [X] T002 Rodar `./mvnw clean test` em `backend/` ainda no Boot 3.5.16 e confirmar que passa. Anotar a linha final `Tests run: N, Failures: 0, Errors: 0, Skipped: S` na seção "Suíte" de `specs/003-migrar-spring-boot-4/baseline-boot3.md` (criar o arquivo com o título `# Baseline do Boot 3.5.16` e a data)
- [X] T003 Gravar a baseline HTTP em `specs/003-migrar-spring-boot-4/baseline-boot3.md`:
  1. Rodar `scripts/dev-env.sh status`. Se 5433, 8080 ou 5173 estiverem ocupadas por outra sessão (checkout principal ou outra worktree), parar e perguntar ao usuário, porque o script reaproveita o que já roda nessas portas.
  2. `scripts/dev-env.sh up` e `scripts/dev-env.sh admin`.
  3. Gravar a saída de `curl -s -i` de cada pedido numa seção própria: `GET /api/health`; `GET /api/works` sem token (401); `POST /api/auth/login` com a conta de admin (mascarar `accessToken`); `GET /api/nao-existe` com token; `GET /api/works/00000000-0000-0000-0000-000000000000` com token (404); preflight `OPTIONS /api/works` com `Origin: http://localhost:5173` e `Access-Control-Request-Method: GET`; o mesmo preflight com `Origin: http://evil.invalid`.
  4. Manter os cabeçalhos de resposta (segurança, CORS, `Set-Cookie` com o valor mascarado) e só mascarar valor de token.
- [X] T004 Guardar o cookie `buruna_refresh` do login de T003 (sem mascarar) num arquivo de rascunho **fora do repositório**: `/tmp/claude-1000/-home-lucascatanio-Projects-buruna/104c5265-034f-4405-8eb8-80cd839ea1c2/scratchpad/refresh-boot3.txt`. Depois rodar `scripts/dev-env.sh down` sem apagar o volume do Postgres. Esse cookie prova em T020 que uma sessão aberta no Boot 3 sobrevive à troca (FR-004, SC-004)

**Checkpoint**: suíte verde no Boot 3.5, total anotado, `baseline-boot3.md` com as sete respostas.

---

## Phase 2: Foundational (caracterização + migração)

**⚠️ CRITICAL**: nenhuma conferência de user story começa antes desta fase terminar verde.

### 2a. Caracterização, ainda no Boot 3.5 (research R10)

- [X] T005 [P] Em `backend/src/test/java/com/buruna/identity/IdentityIntegrationTest.java`, no teste de login com sucesso sem 2FA, acrescentar asserções sobre o corpo bruto (`result.getResponse().getContentAsString()`): contém `"requires2FA":false` e `"accessToken"`; não contém `tempToken` (`@JsonInclude(NON_NULL)`) nem `refreshToken` (`@JsonIgnore`)
- [X] T006 [P] Em `backend/src/test/java/com/buruna/reading/ChapterReadingIntegrationTest.java`, no teste que hoje faz `jsonPath("$.urlsExpireAt").exists()` (perto da linha 178), trocar por `jsonPath("$.urlsExpireAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z"))` (ISO-8601 em UTC, `Instant`). No teste que confere `$.nextChapterId` nulo (perto da linha 328), acrescentar `content().string(containsString("\"nextChapterId\":null"))`, porque `value(nullValue())` também passa com o campo ausente
- [X] T007 [P] Em `backend/src/test/java/com/buruna/work/WorkIntegrationTest.java`, no `postTag_nonexistentCategory_returns404`, acrescentar `jsonPath("$.status").value(404)` e `jsonPath("$.error").isString()`, `jsonPath("$.message").isString()`, `jsonPath("$.path").isString()`, `jsonPath("$.timestamp").isString()`: os cinco campos do `ErrorResponse`
- [X] T008 [P] Em `backend/src/test/java/com/buruna/shared/source/mangadownloader/MangaDownloaderClientTest.java`, criar `shouldReadVolumeAsZero_whenCliPrintsNullVolume`, seguindo o padrão de `shouldParseListing_whenCliPrintsJson`: o CLI falso imprime um capítulo com `"volume": null`, e o resultado tem `volume() == 0`
- [X] T009 Rodar `./mvnw clean test` em `backend/`: verde, com o total de T002 mais 1 (o teste novo de T008). Commit: `test(backend): caracteriza o formato JSON antes do Boot 4 (#124)`, com T005–T008 e `specs/003-migrar-spring-boot-4/baseline-boot3.md`

**Checkpoint**: as asserções descrevem o comportamento do Boot 3.5 e estão verdes. Daqui em diante, nenhuma delas é editada.

### 2b. Troca de versão (research R1–R9)

- [X] T010 Em `backend/pom.xml`:
  - parent `spring-boot-starter-parent` 3.5.16 → `4.1.3`.
  - Apagar o comentário "Sobrescrevem versões gerenciadas…" e as oito propriedades `tomcat.version`, `postgresql.version`, `jackson-bom.version`, `httpclient5.version`, `httpcore5.version`, `commons-lang3.version`, `log4j2.version` e `opentelemetry.version`, com seus comentários. Ficam `java.version`, `pdfbox.version` e `jbig2-imageio.version` (R1).
  - Na exclusão de `commons-logging` do `pdfbox`, o comentário passa a dizer que o `spring-core` já traz o `commons-logging` 1.3 e a exclusão evita a segunda cópia (R9).
  - `spring-boot-starter-web` → `spring-boot-starter-webmvc`.
  - Acrescentar `spring-boot-starter-flyway` (sem ele as migrations não rodam) e `spring-boot-starter-restclient` (R4).
  - `springdoc-openapi-starter-webmvc-ui` 2.8.17 → `3.1.1` (R8).
  - Em test: acrescentar `spring-boot-starter-webmvc-test`. Trocar `org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter` e `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql` (R5).
  - Não mexer em `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `flyway-core`, `flyway-database-postgresql`, `postgresql`, `lombok`, `jjwt-*` 0.12.6, `google-cloud-storage`, `totp`, `httpclient5`, `archunit-junit5` 1.3.0 nem nos plugins.
- [X] T011 Em `backend/src/main/resources/application.yml`, sob `spring:`, acrescentar `jackson: use-jackson2-defaults: true`, com um comentário de uma linha: mantém o JSON igual ao do Boot 3 (FR-009) e desligar é decisão de outra issue (R2)
- [X] T012 [P] Em `backend/src/main/java/com/buruna/BurunaApplication.java`, trocar o import para `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration` (R4)
- [X] T013 [P] Trocar o import `org.springframework.boot.web.client.RestTemplateBuilder` → `org.springframework.boot.restclient.RestTemplateBuilder` em `backend/src/main/java/com/buruna/shared/config/IngestJobConfig.java`, `backend/src/main/java/com/buruna/shared/config/PubSubConfig.java` e `backend/src/main/java/com/buruna/shared/notification/ResendEmailSender.java` (R4)
- [X] T014 [P] Passar para o Jackson 3, no contexto identity (R2): em `backend/src/main/java/com/buruna/identity/web/PasswordResetPushController.java` e `backend/src/main/java/com/buruna/identity/application/account/PubSubPasswordResetRequests.java`, o `com.fasterxml.jackson.databind.ObjectMapper` vira `tools.jackson.databind.ObjectMapper`, e o `com.fasterxml.jackson.core.JsonProcessingException` vira `tools.jackson.core.JacksonException`. Onde a exceção só era declarada em `throws` e relançada, tirar o `throws`. Onde era embrulhada, manter o `catch` com o tipo novo
- [X] T015 [P] Passar para o Jackson 3, em shared (R2):
  - `backend/src/main/java/com/buruna/shared/security/ProxySecretFilter.java` e `backend/src/main/java/com/buruna/shared/source/mangadownloader/MangaDownloaderClient.java`: mesma troca de T014.
  - `backend/src/main/java/com/buruna/shared/converter/StringListConverter.java`: `new ObjectMapper()` → `tools.jackson.databind.json.JsonMapper.builder().build()`, e as exceções viram `JacksonException`.
  - `backend/src/main/java/com/buruna/shared/config/ExternalSourceConfig.java`: o `ObjectMapper` de `tools.jackson` e o `RestTemplateBuilder` de `org.springframework.boot.restclient`.
  - Imports de `com.fasterxml.jackson.annotation.*` não mudam.
- [X] T016 Testcontainers 2 e `AutoConfigureMockMvc` (R4, R5). Nos 15 arquivos abaixo, o `org.testcontainers.containers.PostgreSQLContainer` vira `org.testcontainers.postgresql.PostgreSQLContainer`, e `static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")` vira `static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")`. Nos 12 que usam `AutoConfigureMockMvc`, o import vira `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`. Arquivos em `backend/src/test/java/com/buruna/`:
  - `SmokeApplicationTest.java` (só o Testcontainers)
  - `engagement/EngagementIntegrationTest.java`
  - `identity/IdentityIntegrationTest.java`
  - `identity/inactivity/InactivityJobIntegrationTest.java`
  - `reading/ChapterReadingIntegrationTest.java`
  - `reading/ReadingIntegrationTest.java`
  - `shared/security/ProxySecretIntegrationTest.java`
  - `sourcing/SourcingIntegrationTest.java`
  - `sourcing/SourcingSyncIntegrationTest.java`
  - `work/CatalogLanguageIntegrationTest.java`
  - `work/ChapterUploadIntegrationTest.java`
  - `work/DeletePrivateCollectionForUserIntegrationTest.java` (só o Testcontainers)
  - `work/RegisterChapterIntegrationTest.java` (só o Testcontainers)
  - `work/StorageOrphansJobIntegrationTest.java`
  - `work/WorkIntegrationTest.java`
- [X] T017 Depois de T016, nos quatro testes que usam `ObjectMapper`:
  - `backend/src/test/java/com/buruna/identity/IdentityIntegrationTest.java` e `backend/src/test/java/com/buruna/identity/inactivity/InactivityJobIntegrationTest.java`: o `@Autowired ObjectMapper` passa a `tools.jackson.databind.ObjectMapper`.
  - `backend/src/test/java/com/buruna/shared/security/ProxySecretFilterTest.java`: `new ObjectMapper()` → `JsonMapper.builder().build()`.
  - `backend/src/test/java/com/buruna/shared/source/mangadownloader/MangaDownloaderClientTest.java`: `new ObjectMapper()` → `JsonMapper.builder().configureForJackson2().build()`, igual ao mapper que o Boot monta com `use-jackson2-defaults` (R2, R10).
  - Só imports e a construção do mapper mudam, nenhuma asserção.
- [X] T018 Rodar `./mvnw clean test` em `backend/` e tratar cada falha pela tabela *Riscos* do [plan.md](plan.md):
  - Flyway: confirmar o starter de T010.
  - Validação de esquema do Hibernate 7: ajustar o mapeamento, nunca criar migration (FR-012).
  - Descoberta de testes quebrada pelo motor do ArchUnit: trocar `com.tngtech.archunit:archunit-junit5` por `com.tngtech.archunit:archunit` 1.3.0 em `backend/pom.xml` (R8).
  - Cliente HTTP externo falhando na desserialização: corrigir o DTO (tipo wrapper ou `@JsonIgnoreProperties`), não criar mapper global (R3).
  - Cada correção que não estiver no research ganha uma linha em `specs/003-migrar-spring-boot-4/research.md`, na decisão correspondente.
  - Dependência sem versão compatível com o Boot 4: parar a implementação e voltar para a spec (edge case da spec).
- [X] T019 Checkpoint da migração:
  - `./mvnw clean test` verde, com `Tests run` ≥ total de T009 e `ArchitectureTest` passando.
  - Mostrar a saída.
  - `git diff HEAD -- backend/src/test` não altera nenhuma asserção de T005–T008, só imports e mappers.
  - `grep -rn "com.fasterxml.jackson.databind\|com.fasterxml.jackson.core" backend/src` não acha nada.
  - `git diff --stat dev -- frontend/` sai vazio (FR-014).
  - Commit: `chore(backend): Spring Boot 4.1.3 (#124)`, com corpo listando os CVEs e as oito sobrescritas removidas.

**Checkpoint**: backend no Boot 4.1.3, suíte verde e caracterização intacta.

---

## Phase 3: User Story 1 - Quem lê não percebe a troca (Priority: P1) 🎯 MVP

**Goal**: login, sessão, leitura, erros e borda iguais aos do Boot 3.5.

**Independent Test**: sessão aberta no Boot 3 continua válida no Boot 4. As respostas da
baseline, inclusive o CORS, são iguais, salvo `timestamp`, `Date` e valores de token.

**Cobertura automatizada** (já na suíte de T019): `IdentityIntegrationTest` (login,
refresh, 2FA, logout, cadastro, aprovação, reset e push do Pub/Sub), `ReadingIntegrationTest`,
`ChapterReadingIntegrationTest`, `EngagementIntegrationTest` e `ProxySecretIntegrationTest`.

- [X] T020 [US1] Subir o app no Boot 4 (`scripts/dev-env.sh status`, com a mesma regra de portas de T003, e depois `scripts/dev-env.sh up`). Fazer `POST /api/auth/refresh` com o cookie guardado em T004 e conferir que responde 200 com `accessToken` novo, sem novo login (FR-004, SC-004, US1 cenário 1). O cookie ainda vale porque T003 não chamou o refresh e ele dura 7 dias. Depois, apagar `/tmp/claude-1000/-home-lucascatanio-Projects-buruna/104c5265-034f-4405-8eb8-80cd839ea1c2/scratchpad/refresh-boot3.txt`
- [X] T021 [US1] Repetir os `curl -s -i` de T003, inclusive os dois preflights de CORS, e comparar com `specs/003-migrar-spring-boot-4/baseline-boot3.md`. Só podem diferir `timestamp`, `Date` e valores de token. Uma diferença em status, campo, cabeçalho de segurança, cabeçalho CORS ou `Set-Cookie` é corrigida na configuração (por exemplo, `server.error.*` no `backend/src/main/resources/application.yml`) e registrada em `research.md` (FR-009, FR-010, FR-011, US1 cenários 4–5)
- [X] T022 [US1] Checkpoint: `./mvnw clean test` em `backend/` verde, com a saída mostrada (constituição, Fluxo item 3), mesmo que T021 não tenha mudado nada

**Checkpoint**: US1 conferida e suíte verde. Já daria para fazer o deploy só com a garantia
da leitura.

---

## Phase 4: User Story 2 - Quem publica e quem administra também não percebe (Priority: P2)

**Goal**: envio, processamento, import e aprovação iguais aos de hoje.

**Independent Test**: no app local, um capítulo de cada formato é publicado e o arquivo
falso é recusado.

**Cobertura automatizada** (já na suíte de T019):
- `ChapterUploadIntegrationTest`, `RegisterChapterIntegrationTest`, `WorkIntegrationTest`, `CatalogLanguageIntegrationTest` e os testes de `shared/media` (envio e arquivo hostil).
- `SourcingIntegrationTest` e `SourcingSyncIntegrationTest` (import e sincronização).
- `IdentityIntegrationTest` (aprovação e reset, US2 cenários 4–5).
- Os clientes que desserializam pelo Jackson 3 do `RestTemplate` (R3): `MangaDexSourceTest`, `AniListSourceTest`, `CaptchaServiceTest`, `PubSubPublisherTest`, `CloudRunJobLauncherTest`, `ResendEmailSenderTest` e `MangaDownloaderClientTest`.

- [X] T023 [US2] Com o app de T020 no ar, enviar um capítulo em PDF, um CBZ e um EPUB a uma obra do admin, e um `.pdf` falso. Conferir que os três são publicados e o falso é recusado com a mesma mensagem de antes. Esses são os passos 6–7 do [quickstart.md](quickstart.md), feitos aqui por `curl` ou pelo navegador para pegar a quebra cedo. O `/validar-quickstart` repete tudo no fim (FR-006, US2 cenários 1–2)
- [X] T024 [US2] Checkpoint: `./mvnw clean test` em `backend/` verde, com a saída mostrada

**Checkpoint**: US1 e US2 conferidas.

---

## Phase 5: User Story 3 - As rotinas agendadas continuam rodando (Priority: P3)

**Goal**: inatividade, órfãos e sincronização iguais, e a recusa sem credencial igual.

**Independent Test**: os três disparos locais respondem como antes, e rodar a sincronização
duas vezes não duplica nada.

**Cobertura automatizada** (já na suíte de T019): `InactivityJobIntegrationTest`,
`StorageOrphansJobIntegrationTest`, `SourcingSyncIntegrationTest` e
`DeletePrivateCollectionForUserIntegrationTest`.

- [X] T025 [US3] Com o app de T020 no ar:
  - `POST /api/admin/jobs/inactivity` e `POST /api/admin/jobs/storage-orphans` com `X-Job-Secret: dev-secret`: 200 com o resumo.
  - Os mesmos pedidos sem o header: a mesma recusa de hoje.
  - `POST /api/admin/sourcing/run` duas vezes seguidas, como admin: nenhum capítulo duplicado.
  - Esses são os passos 11–13 do quickstart. Depois, `scripts/dev-env.sh down`.
  - Cobre FR-008 e US3 cenários 1–3.
- [X] T026 [US3] Checkpoint: `./mvnw clean test` em `backend/` verde, com a saída mostrada

**Checkpoint**: as três stories conferidas localmente.

---

## Phase 6: Polish, docs e validação

- [X] T027 [P] Em `.specify/memory/constitution.md`:
  - Em *Padrões técnicos*, "Spring Boot 3.5" → "Spring Boot 4.1".
  - O rodapé passa a `**Versão**: 1.2.1 | **Ratificada**: 2026-11-15 | **Última emenda**: 2026-11-21`. É emenda PATCH, só redação de versão (R12).
- [X] T028 [P] Em `README.md`, trocar o badge `Spring%20Boot-3.5.16` → `Spring%20Boot-4.1.3` e o texto "Spring Boot 3.5.16" → "Spring Boot 4.1.3" (R12)
- [X] T029 Rodar `grep -rn "3\.5\.16\|Boot 3\.5" --exclude-dir=node_modules --exclude-dir=specs --exclude-dir=.git .` e confirmar que não sobrou menção à versão antiga fora das specs. Commit: `docs: Spring Boot 4.1 na constituição e no README (#124)` (FR-015)
- [ ] T030 Varredura (passos V1–V4 do [quickstart.md](quickstart.md)):
  - `docker build --target service -t buruna-backend:boot4 backend`.
  - `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest image --severity CRITICAL,HIGH buruna-backend:boot4`.
  - Zero CRITICAL e sem CVE-2026-47884/47890. Se aparecer CRITICAL com correção, voltar a sobrescrita com o motivo ao lado em `backend/pom.xml` (FR-003) e repetir T019.
  - Guardar o resumo da saída para o `validation.md`.
- [ ] T031 Rodar `/speckit-converge` e voltar ao implement para cada lacuna até ele não achar nenhuma
- [ ] T032 Checkpoint da fase: `./mvnw clean test` em `backend/` verde, com a saída mostrada. `git diff --stat dev -- frontend/` sai vazio (FR-014)
- [ ] T033 Rodar `/validar-quickstart` (roteiro local completo, passos 1–18). O resultado vai em `specs/003-migrar-spring-boot-4/validation.md` e precisa ficar **APROVADA**
- [ ] T034 Rodar `/engineering:code-review` no diff contra a `dev` e corrigir o que for real. Depois, repetir T032. Deixar pronta a descrição do PR para `dev`: cita o review, a pasta `specs/003-migrar-spring-boot-4` e `Refs #124` (não `Closes`, porque o merge na `dev`, a branch padrão, fecharia a issue antes do pós-deploy). Push e abertura do PR só quando o usuário pedir

---

## Phase 7: Depois do deploy (produção)

Executado no release `dev` → `main`. As mutações do `gcloud` ficam com o usuário.

- [ ] T035 Antes do deploy:
  - Deixar uma aba logada em produção.
  - Anotar a revisão atual do serviço (`gcloud run services describe buruna-backend --region us-east1 --format='value(status.latestReadyRevisionName)'`) e a imagem atual do Job (`gcloud run jobs describe buruna-ingest --region us-east1 --format='value(spec.template.spec.template.spec.containers[0].image)'`), para a volta (SC-006).
  - Depois do deploy, executar os passos P1–P10 do [quickstart.md](quickstart.md) e registrar o resultado na seção "Depois do deploy" de `specs/003-migrar-spring-boot-4/validation.md`.
  - O `gcloud scheduler jobs run …` (P6) e a volta de revisão, se for preciso, ficam com o usuário.
- [ ] T036 Uma semana depois do deploy, conferir o P10 (erros do Cloud Run e feedback) e fechar a #124 se não houver nada atribuível à migração (SC-005)

---

## Dependencies & Execution Order

- **Setup (T001–T004)**: T002 antes de T003. T004 depende do login de T003.
- **Foundational 2a (T005–T009)**: T005–T008 em paralelo, porque são arquivos diferentes.
  T009 fecha o commit 1. Tudo ainda no Boot 3.5.
- **Foundational 2b (T010–T019)**: T010 e T011 primeiro. T012–T015 em paralelo depois deles.
  T016 → T017, porque dois arquivos coincidem. T018 → T019. Não compila antes de T010–T017
  estarem todos feitos.
- **US1 (T020–T022)**, **US2 (T023–T024)**, **US3 (T025–T026)**: dependem de T019. T020
  sobe o app que T021, T023 e T025 usam. Cada fase fecha com a suíte completa.
- **Polish (T027–T034)**: T027 e T028 em paralelo a qualquer momento depois de T019.
  T030 antes de T033. T031 → T032 → T033 → T034.
- **Pós-deploy (T035–T036)**: depois do merge e do release.

## Parallel Opportunities

```text
# 2a: as quatro caracterizações
T005 IdentityIntegrationTest  |  T006 ChapterReadingIntegrationTest
T007 WorkIntegrationTest      |  T008 MangaDownloaderClientTest

# 2b: depois do pom e do yml
T012 BurunaApplication  |  T013 RestTemplateBuilder (3 arquivos)
T014 Jackson identity   |  T015 Jackson shared (4 arquivos)

# Docs
T027 constituição  |  T028 README
```

## Implementation Strategy

1. **MVP = Foundational + US1.** Com a suíte verde e a baseline igual, a leitura (o uso de
   quase todo mundo) está garantida. US2 e US3 são conferência das partes com mais
   integração externa.
2. Três commits de código e docs, mais o da spec (T001): `docs(specs)…`, `test(backend)…`,
   `chore(backend)…` e `docs…`, cada um com a suíte verde. Sem rodapé de co-autoria de IA.
3. Parar em cada checkpoint, rodar `./mvnw clean test` e mostrar a saída. Sem teste verde, a
   fase não avança.
4. Sem push nem PR antes de o usuário pedir. O deploy em produção e o `gcloud` ficam com o
   usuário.
