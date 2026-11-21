# Arquitetura

> Documento de referência para quem vai trabalhar no projeto sem contexto prévio.
> Descreve o estado **atual** do backend (Clean Architecture por bounded context) e os
> fluxos de usuário principais. Para infraestrutura de deploy, veja
> [DEPLOYMENT.md](DEPLOYMENT.md); para modelo de dados, [DATABASE.md](DATABASE.md); para
> segurança, [SECURITY.md](../SECURITY.md). As regras que todo código segue estão na
> [constituição](../.specify/memory/constitution.md); as decisões que levaram a elas, no
> [§7](#7-registro-de-decisões). Feature nova é especificada em `specs/` antes do código.

## 1. Visão geral

O backend é um **monolito modular** (não microsserviços — ver [ADR-01](#adr-01)),
organizado em **Clean Architecture por bounded context**. Cada contexto de negócio tem
suas próprias camadas `domain/ → application/ → persistence/ → web/`, com uma regra de
dependência guardada por teste automatizado (`ArchitectureTest`, ArchUnit) — não apenas
por convenção de code review.

Trabalho longo fica fora das requisições, num **Cloud Run Job** (`buruna-ingest`, ADR-48): o
mesmo jar do backend, no profile `ingest`, extrai as páginas dos capítulos enviados e baixa os
capítulos importados do MangaDex. O backend dispara o Job depois do commit; o Cloud Scheduler o
dispara a cada 3 h para o sync das obras acompanhadas (ADR-49). No profile `local`, o mesmo
trabalho roda na própria requisição.

O frontend é uma SPA React sem Clean Architecture própria: apenas uma camada leve
`api/` (chamadas HTTP tipadas) + `types/` (contratos TypeScript), ver §5.

## 2. Os bounded contexts

| Contexto | Pacote | Responsabilidade |
|---|---|---|
| **identity** | `com.buruna.identity` | Autenticação (JWT + refresh + 2FA), cadastro/aprovação, gestão de usuários (fusão de auth+user) |
| **work** | `com.buruna.work` | Catálogo público (com idiomas), coleção privada, capítulos (páginas, ou arquivo PDF/EPUB no livro), volumes legados, tags, submissão/promoção, processamento do upload |
| **reading** | `com.buruna.reading` | Leitor (manifesto do capítulo com URLs assinadas, PDF de volume e livro), progresso por capítulo (página, ou CFI no EPUB), histórico |
| **engagement** | `com.buruna.engagement` | Avaliações (ratings) e lista de leitura |
| **sourcing** | `com.buruna.sourcing` | Importação do MangaDex e dos sites do manga-downloader: obras acompanhadas, versões de capítulo por grupo, lease de um executor por fonte (ADR-47, ADR-54) |
| **admin** | `com.buruna.admin` | Casca administrativa — dashboard, jobs, revisão de submissões. Sem `domain` próprio; a `application` (`DashboardService`) só orquestra os use cases públicos dos outros contextos |

Dois pacotes adicionais fora desse modelo, por não serem bounded contexts de domínio:

- `shared/` — infraestrutura cross-context: `StorageClient` (GCS/local), `EmailSender`,
  `Clock` injetável, `GlobalExceptionHandler`, config de segurança, e ainda:
  `shared/media` (extração de páginas de CBZ/CBR/PDF, `ImageInspector`, validação de livro),
  `shared/source` (porta `ExternalSource` e o adaptador do MangaDex; porta `WorkMetadataSource` e
  o adaptador do AniList; `MangaDownloaderClient`, que roda a CLI), `shared/jobs` (disparo do
  Cloud Run Job) e `shared/transaction` (`AfterCommit`).
- `feedback/` — módulo utilitário isolado (`POST /feedback`), sem lógica de domínio
  suficiente para justificar camadas próprias.

> DTO que um use case recebe ou devolve mora na `application/` do contexto; na `web/` só
> fica o que apenas o controller usa (ADR-31). O `ArchitectureTest` cobra isso em todos os
> contextos migrados.

## 3. Camadas e regra de dependência

```
        web  ─────────►  application  ─────────►  domain
                              │                      ▲
                              ▼                      │
                         persistence  ──────────────┘
                              │
                              ▼
                    (portas: StorageClient, EmailSender — em shared/)
```

- **`domain/`** — agregados ricos (invariantes como método, sem setter público), Value
  Objects, exceções de domínio puras (sem `HttpStatus`), domain services só quando a
  regra não pertence a nenhuma entidade e depende de porta externa (ex.: `QuotaService`,
  `SlugAllocator`). Não depende de nada — nem Spring, nem outro contexto.
  Domínio é anotado com JPA na própria classe, sem entidade de domínio separada da
  entidade de persistência — ver [ADR-32](#adr-32).
- **`application/`** — casos de uso (um por intenção, ex.: `PromoteWorkUseCase`,
  `RunInactivityUseCase`), DTOs, fronteira transacional. Consome `domain` + portas
  (`StorageClient`, `EmailSender`, repositórios).
- **`persistence/`** — interfaces Spring Data JPA. Repositórios não têm ports/abstrações
  customizadas — decisão deliberada, ver [ADR-37](#adr-37).
- **`web/`** — controllers finos: extraem `actorId` (via `@AuthenticationPrincipal`) e
  `@PreAuthorize`, delegam ao use case, nunca contêm lógica de negócio.

Regra completa de camadas e proporcionalidade (contextos quase-CRUD podem ter um único
application service): [ADR-31](#adr-31).
Fronteiras de agregado (`Work` raiz com `Volume`, um agregado por caso público/privado):
[ADR-34](#adr-34).

### Cross-context

- Um contexto **nunca** importa `domain`/`persistence` de outro — só o `application`
  (use case público) do outro contexto.
- Referências cross-contexto são por **UUID** (`actorId`, `ownerId`), nunca a entidade
  de outro contexto.
- `@Query(nativeQuery=true)` e `JOIN` entre tabelas de contextos diferentes são
  proibidos — passe por um use case público. Ver [ADR-39](#adr-39)
  para o caso concreto que motivou a regra (`reading` lendo `volumes` de `work`).
- RBAC (`@PreAuthorize`) na borda; ownership (posse) verificada na `application` via
  `actorId`. Autorização unificada e fim do acoplamento `identity ↔ work`:
  [ADR-35](#adr-35).

### Exceções

Exceções de domínio são puras (`DomainErrorType`, sem `HttpStatus`); a tradução para
resposta HTTP acontece só no `GlobalExceptionHandler` (`shared/exception/`), retornando
`ErrorResponse {status, error, message, path, timestamp}`. Nenhuma exceção carrega
`HttpStatus`. Ver [ADR-33](#adr-33).

## 4. ArchUnit como guarda de arquitetura

`ArchitectureTest` (`backend/src/test/java/com/buruna/architecture/ArchitectureTest.java`)
falha o build (`./mvnw clean test`) se a fronteira for violada. Três regras:

1. **`domainAndApplication_shouldNotImportInternalsOfOtherContexts`** — nenhuma classe em
   `domain/`/`application/` de um contexto migrado pode depender de `domain/`/`persistence/`
   de outro.
   `admin` está entre os contextos migrados: sua `application` só consome `application`
   de outros contextos.
2. **`domainAndApplication_shouldNotDependOnWebLayer`** — `domain/`/`application/` não
   dependem da `web/` do próprio contexto (seta do ADR-31). Vale para todos os contextos
   migrados.
3. **`persistenceLayer_shouldNotUseNativeQueries`** — detecta `@Query(nativeQuery=true)`
   nas camadas `persistence` de contextos migrados.

Nenhuma regra restringe o que a camada `web/` importa, de propósito: um controller recebe
`identity.domain.User` via `@AuthenticationPrincipal` e extrai `user.getId()` antes de
delegar (ex.: `engagement.web.RatingController`). Esse import cross-contexto na `web/` é
o padrão aceito, não uma violação.

O que nenhum guard cobre (JPQL referenciando entidade de outro contexto por nome de
string) é review-only: a revisão de PR cobra (constituição, princípio II).

## 5. Frontend leve

Sem Clean Architecture no frontend — o custo não se paga para uma SPA
(ver [ADR-31](#adr-31)). Apenas duas
convenções:

- `frontend/src/api/` — uma chamada Axios tipada por contexto (`identityApi.ts`,
  `workApi.ts`, `privateWorkApi.ts`, `chapterApi.ts`, `readingApi.ts`, `engagementApi.ts`, `adminApi.ts`,
  `sourcingApi.ts`, `feedbackApi.ts`).
- `frontend/src/types/` — contratos TypeScript espelhando os DTOs do backend, um arquivo
  por contexto.

## 6. Fluxos de usuário

### 6.1 Cadastro e aprovação

```
VISITANTE               BROWSER                    BACKEND (identity)              ADMIN
   │── preenche form ────►│                              │                            │
   │                      │── POST /auth/register ──────►│                            │
   │                      │                              │ rate limit (5/h) + hCaptcha│
   │                      │                              │ User{PENDING} + BCrypt     │
   │                      │                              │─── e-mail admins (lote) ──►│
   │                      │◄── 201 Created ──────────────│                            │
   │                      │                              │◄── GET /admin/users/pending│
   │                      │                              │─── lista pendentes ───────►│
   │                      │                              │◄── POST /admin/users/{id}/approve │
   │                      │                              │ status→ACTIVE, e-mail      │
   │◄── e-mail: aprovado ─────────────────────────────────────────────────────────────┘
```

Use case: `identity.application.admin.UserService` (aprovação/rejeição), controller
`AdminUserController`.

### 6.2 Autenticação

`identity.application.authentication`: `AuthenticationService` (login, refresh, logout),
`TokenService` (JWT), `TotpService` (2FA). Fluxo completo — incluindo 2FA, refresh
rotation e reset de senha — em [SECURITY.md](../SECURITY.md#ciclo-de-vida-do-jwt--refresh-token).

### 6.3 Leitura de obra

```
BROWSER                              BACKEND (reading)                    GCS
  │── GET /works?page=0&size=20 ───►│ (work.application.CatalogQueryUseCase / FindPublicWorkUseCase)
  │◄── lista de obras ───────────────│
  │── GET /works/{slugOrId} ──────────►│
  │◄── detalhes + volumes ───────────│
  │── GET /reader/{volumeId}/url ────►│
  │                                  │── signed URL (GetVolumeAccessUseCase) ──►│
  │                                  │◄── signed URL (30 min) ──────────────────│
  │                                  │ incrementa view_count + ReadingHistory   │
  │◄── { url } ───────────────────────│
  │── GET <signed URL> ────────────────────────────────────────────────────────►│
  │◄── PDF binário ─────────────────────────────────────────────────────────────│
  │── POST /reader/{volumeId}/progress { currentPage } ──►│ upsert ReadingProgress
```

A listagem traz os idiomas de cada obra e aceita `?language=` (`GET /works/languages` dá a lista
para o filtro); volume legado conta como pt-BR ([ADR-50](#adr-50)).
As rotas antigas `/mangas...` saíram depois da v2.0.0 ([ADR-45](#adr-45)).

Controller `reading.web.ReaderController`, serviço `reading.application.ReadingService`.
Se a signed URL expirar (403 do GCS), o frontend pede uma nova via o mesmo endpoint.

### 6.3.1 Leitura de capítulo

```
BROWSER                                  BACKEND                                     GCS
  │── GET /works/{id}/chapters/languages ►│ (work) ListChaptersUseCase                 │
  │── GET /works/{id}/chapters?language= ►│ publicados, em ordem de leitura            │
  │── GET /reader/works/{id}/chapter-progress ►│ (reading) "continuar lendo" + lidos   │
  │── GET /reader/chapters/{id} ─────────►│ (reading) → work.GetChapterForReadingUseCase
  │                                       │   acesso, view_count, vizinhos no idioma   │
  │                                       │ histórico + WindowedSignedUrls (ADR-07)     │
  │◄── páginas [{url, w, h}], anterior/próximo, urlsExpireAt ─│                         │
  │── GET <url da página> (prefetch) ─────────────────────────────────────────────────►│
  │── POST /reader/chapters/{id}/progress { currentPage } ►│ total vem do capítulo     │
```

Capítulo em processamento, que falhou ou tirado do ar responde 404; capítulo de obra privada
de outro usuário, 403. As listas de quem lê só têm capítulos publicados; o dono da obra (e o
ADMIN, no catálogo) vê também os em processamento e os que falharam, com o motivo.

**Livro (capítulo-arquivo):** numa obra `LIVRO` a lista traz as edições, e o manifesto traz
`file {url, format, pageCount}` em vez de páginas. O front (`BookChapterReader`) abre PDF no
pdf.js (`PdfDocumentReader`, o mesmo do volume, por trechos) e EPUB no foliate-js
(`EpubReader`, arquivo inteiro, carregado só quando alguém abre um EPUB). O progresso vai como
`{ currentPage }` no PDF e `{ position, percent }` (CFI e 0 a 1) no EPUB; com 99% o livro
conta como lido. URL vencida é renovada pelo manifesto com `?prefetch=true`, que não grava
histórico de novo. Isolamento do EPUB: [ADR-51](#adr-51).

### 6.4 Upload de volume público (duas fases)

Use cases: `GeneratePublicVolumeUploadUrlUseCase` (fase 1 — gera Signed URL de PUT para
`pending/volumes/{workId}/{uuid}.pdf`, um caminho VINCULADO à obra via o Value Object
`work.domain.VolumeObjectName`) e `FinalizePublicVolumeUseCase` (fase 2 — valida que o
`objectName` recebido é um pendente da PRÓPRIA obra, lê metadados do blob via
`blob.getMd5()`, move o objeto para `volumes/{workId}/{uuid}.pdf` e persiste `Volume`).
Controller `work.web.VolumeController`
(`POST /works/{id}/volumes/upload-url`, `POST /works/{id}/volumes/finalize`). O
backend nunca toca os bytes do arquivo — ver [ADR-24](#adr-24),
[ADR-25](#adr-25) e [ADR-40](#adr-40)
(objectName vinculado à obra + prefixo `pending/`).

### 6.4.1 Upload de capítulo (CBZ, CBR ou PDF)

Mesmo desenho em duas fases do volume, com a extração das páginas fora da requisição
([ADR-48](#adr-48)). Rotas `/my/works/{id}/chapters/...`
(coleção privada, com quota; `PrivateChapterController`) e `/works/{id}/chapters/...`
(catálogo, colaborador dono ou ADMIN; `ChapterController`).

```
BROWSER                       BACKEND (work)                              GCS / Cloud Run Job
  │── POST .../upload-url ───►│ número livre? (aviso antecipado)           │
  │◄── URL de PUT ────────────│ pending/chapters/{workId}/{uuid}.cbz        │
  │── PUT <signed URL> ─────────────────────────────────────────────────────►│
  │── POST .../finalize ─────►│ ChapterObjectName.parsePending (ADR-40)     │
  │                           │ quota (privado) + RegisterChapterUseCase     │
  │                           │   (lock na obra, ADR-53) → PROCESSING        │
  │                           │ move → chapter-sources/                      │
  │◄── 201 PROCESSING ────────│ após o commit: jobs.run(chapterId) ─────────►│ buruna-ingest
  │                           │                                              │ extrai páginas →
  │                           │                                              │ chapters/{id}/{n}.{ext}
  │                           │                                              │ PUBLISHED ou FAILED
```

Use cases: `GenerateChapterUploadUrlUseCase`, `FinalizeChapterUploadUseCase`,
`ProcessChapterSourceUseCase` (no Job), `RetryChapterIngestUseCase`, `DeleteChapterUseCase`.
A extração (`PageExtractor`, com CBZ via `ZipFile`, CBR via `bsdtar` e PDF via PDFBox, mais o
`ImageInspector`) mora em `shared/media`. Em obra `LIVRO`, PDF não vira imagens. Apagar obra,
coleção ou conta também apaga os objetos dos capítulos (`ChapterStorageCleaner`).

### 6.4.2 Importação do MangaDex

ADM, em `/admin/sourcing` (`AdminSourcingController`). A fonte fica atrás de
`shared.source.ExternalSource`; o estado
mora no contexto `sourcing` ([ADR-47](#adr-47)).

```
BROWSER (ADM)                     BACKEND (sourcing → work)                    Cloud Run Job
  │── GET /search?q= ────────────►│ ExternalSource.search (+ já acompanhada?)    │
  │── GET /works/{id}/groups ────►│ grupos do idioma, por nº de capítulos        │
  │── POST /imports ─────────────►│ metadados e capa (fora da transação)         │
  │                               │ lock no lease: já acompanhada? → 409         │
  │                               │ work.CreateImportedWorkUseCase (ou vínculo)  │
  │                               │ TrackedWork; após o commit: jobs.run ───────►│ --sourcing.run
  │◄── 201 ───────────────────────│                                              │ lease do MangaDex
  │                               │                                              │ lista versões → escolha
  │                               │                                              │ por versão PENDING:
  │                               │                                              │  RegisterChapter (PROCESSING)
  │                               │                                              │  páginas original + data-saver
  │                               │                                              │  PublishImportedChapter
```

O sync ([ADR-49](#adr-49)) é o mesmo Job com `--sourcing.sync=true`,
disparado pelo Cloud Scheduler a cada 3 h: lista todas as obras ativas, compara com o que já
conhece (versão nova, removida na fonte, páginas trocadas, falha a tentar de novo) e, no
primeiro sync depois das 8 h, manda o e-mail diário aos ADMs se houver novidade. As ações do ADM
ficam em `/admin/sourcing/tracked/{id}/...` (verificar agora, pausar, retomar, avisos, grupos,
ignorar grupo) e `/admin/sourcing/source-chapters/{id}/...` (tentar de novo, baixar de novo,
usar esta versão).

`POST /admin/sourcing/run` pede uma rodada sem importar (retoma o que ficou pendente) e
`GET /admin/sourcing/tracked` lista as obras acompanhadas com a contagem por estado. Na página
da obra, `GET /works/{id}/source` dá o crédito da fonte. No profile local a rodada acontece na
própria requisição, depois do commit (`InlineSourcingTrigger`).

Sites do manga-downloader ([ADR-54](#adr-54)), ainda
sem tela: `GET /admin/sourcing/site-listing?url=` roda o `list --json` da CLI no serviço (idiomas,
grupos e capítulos, inclusive os bloqueados), e `POST /admin/sourcing/imports` com
`"source":"MANGA_DOWNLOADER"`, a URL em `externalId` e `linkWorkId` vincula a série a uma obra
existente; com `aniListId` no lugar, anexa à obra que já tem esse id ou cria uma com os metadados
do AniList e a sinopse revisada pelo ADM (`synopsis`), e a obra guarda o id (`works.anilist_id`). O executor é o mesmo: só listar e baixar ficam atrás de `ChapterSource`, um adaptador
por fonte. Cada fonte tem o seu lease e a sua execução do Job (`--sourcing.source`); série do
mangadex.org segura também o lease do MangaDex e, ocupado, fica para a próxima rodada. As páginas
vêm do CBZ que a CLI grava, abertas pelo `PageExtractor` do upload.

Metadados do AniList (ADR-54), ainda sem tela: `GET /admin/sourcing/metadata/search?q=` busca, `GET /admin/sourcing/metadata/works/{id}`
mostra a obra como o import vai criá-la (mesmo `WorkMetadataMapper`, tags no vocabulário do
catálogo) e `GET /admin/sourcing/metadata/cover?url=` serve a capa, só de `s4.anilist.co`, porque
o CSP do site não libera imagens de fora.

### 6.5 Upload privado + submissão/promoção

Controller `work.web.PrivateWorkController` (`/my/works`). Use cases:
`CreatePrivateWorkUseCase` → `GenerateVolumeUploadUrlUseCase` → `FinalizeVolumeUseCase`
para criar obra + volume na coleção privada; `SubmitForApprovalUseCase` para submeter
à revisão (`AdminSubmissionController`, `ReviewSubmissionUseCase` no approve/reject);
`PromoteWorkUseCase` para promoção direta (`COLLABORATOR`+) sem revisão. Os dois
caminhos (promote × submit→approve) coexistem por decisão de domínio.

Validação de unicidade no promote/aprovação é só contra obras públicas — ver
[ADR-17](#adr-17) e [ADR-18](#adr-18).

> `WorkSubmissionStatus` é `PENDING` → `APPROVED` | `REJECTED`; a promoção direta encerra uma
> submissão aberta sem status — ver [`docs/glossario-dominio.md`](glossario-dominio.md) §3.

### 6.6 Inatividade automática

Use case público: `identity.application.admin.RunInactivityUseCase`, disparado por
`admin.web.JobController` (`POST /admin/jobs/inactivity`, autenticado via
`X-Job-Secret`), chamado diariamente pelo Cloud Scheduler (ADR-42). A política de "quantos
dias até aviso/desativação" é domínio puro (`InactivityPolicy`) testável sem framework,
usando `java.time.Clock` injetável — ver [ADR-36](#adr-36).
Usuários `ACTIVE` são processados em páginas de 50 via `Pageable`.

Para deletar a coleção privada de um usuário desativado, o `identity` chama o use case
público `work.application.maintenance.DeletePrivateCollectionForUserUseCase` — exemplo
concreto de cross-context via `application`, não via acesso direto a `persistence`.

### 6.6.1 Arquivos órfãos de volumes

O delete de volume apaga a linha na transação e o arquivo do GCS depois, best-effort
([ADR-24](#adr-24)): se o GCS falha, o arquivo fica em `volumes/` sem linha em `volumes`. O use
case público `work.application.maintenance.DeleteOrphanVolumeFilesUseCase`, disparado por
`admin.web.JobController` (`POST /admin/jobs/storage-orphans`, mesmo `X-Job-Secret` da
inatividade), lista `volumes/` via `StorageClient.list`, confere os nomes contra
`volumes.file_url` e apaga o que não tem linha E foi criado há mais de 7 dias (carência,
`Clock` injetado). Falha em um objeto é logada em WARN e o job segue. `?dryRun=true` só conta,
e uma trava aborta sem apagar nada se os órfãos passarem de 5 e de 10% do analisado. Roda
semanalmente no Cloud Scheduler (ver [DEPLOYMENT.md](DEPLOYMENT.md)).

### 6.7 Deleção de conta

`DELETE /auth/account` (`identity.web.AuthController`) recebe a senha e, com 2FA ativo, o
código TOTP. `identity.application.account.DeleteAccountUseCase` orquestra, sem transação
própria, no mesmo desenho da inatividade:

1. `AccountService.confirmOwnership` confere senha e TOTP. Falha → `403`, com a falha de
   TOTP gravada (`noRollbackFor`).
2. `DeletePrivateCollectionForUserUseCase` apaga a coleção privada na transação de `work`.
3. `AccountService.anonymize` apaga refresh tokens e tokens de reset e chama
   `User.anonymize()`: e-mail e username viram `removido-<id>`, a senha deixa de conferir,
   o 2FA é desligado e o status vira `DELETED`.
4. Os arquivos (volumes privados e avatar) saem do storage depois, best-effort.

A linha do usuário fica porque `works.owner_id` e `volumes.uploaded_by` são `RESTRICT`: o
conteúdo público que ele criou ou enviou continua no catálogo, apontando para a conta
anonimizada. A coleção sai antes da anonimização para que uma falha no meio deixe a conta
ainda utilizável e o pedido possa ser repetido.

## 7. Registro de decisões

Resumo das decisões de arquitetura tomadas até a adoção do Spec Kit. Os números `ADR-NN`
continuam citados em comentários do código e nos outros docs, e a âncora de cada linha
(`#adr-nn`) é o destino desses links. As regras que valem para código novo estão na
[constituição](../.specify/memory/constitution.md). O texto completo de cada ADR (contexto,
alternativas, atualizações) está no
[histórico do git](https://github.com/lucascatanio/buruna/tree/2104cfa623b72a799fa520b047ce49b70d9baa85/docs/adr).

Decisão nova não ganha ADR: fica no `research.md` da spec que a tomou e, se valer para o
projeto todo, vira uma linha aqui (com a spec como origem) ou uma emenda da constituição.

| ID | Decisão | Situação |
|---|---|---|
| <a id="adr-01"></a>ADR-01 | Monolito modular em Spring Boot, não microsserviços | Vigente |
| <a id="adr-02"></a>ADR-02 | Upload de volume passando pelo backend (multipart) | Substituída pela ADR-24 |
| <a id="adr-03"></a>ADR-03 | Sem `@Async` nem `@Scheduled`: e-mail síncrono com timeout de 5 s; agendamento só pelo Cloud Scheduler (inatividade, órfãos, sync), porque o `cpu-throttling` do Cloud Run corta a CPU fora da requisição | Vigente (decisão original revertida em 2026-09 e 2026-10) |
| <a id="adr-04"></a>ADR-04 | nginx serve a SPA e faz proxy de `/api/*` para o backend; sem API Gateway | Vigente |
| <a id="adr-05"></a>ADR-05 | Paginação offset/page com `Pageable` | Vigente |
| <a id="adr-06"></a>ADR-06 | BCrypt para senhas | Vigente |
| <a id="adr-07"></a>ADR-07 | Bucket privado com URL assinada V4 (PDF 30 min, capa 1 h, PUT 15 min); páginas de capítulo por janela de 1 h (`WindowedSignedUrls`) para o cache do navegador funcionar; URL não é revogável, risco aceito | Vigente |
| <a id="adr-08"></a>ADR-08 | UptimeRobot (5 min) como monitoramento | Vigente |
| <a id="adr-09"></a>ADR-09 | Índice manual só onde há consulta frequente em tabela que cresce com o uso | Vigente |
| <a id="adr-10"></a>ADR-10 | Rate limit por IP (em memória) + hCaptcha no cadastro | Vigente |
| <a id="adr-11"></a>ADR-11 | Títulos alternativos e avisos de conteúdo como JSON em `TEXT`, não `TEXT[]` | Vigente |
| <a id="adr-12"></a>ADR-12 | Filtro de tags com semântica AND | Vigente |
| <a id="adr-13"></a>ADR-13 | `GET /works/{slugOrId}` resolve UUID ou slug no mesmo endpoint | Vigente (alias `/mangas` removido depois da v2.0.0) |
| <a id="adr-14"></a>ADR-14 | `client_max_body_size 600M` e `proxy_request_buffering off` no nginx | Vigente como rede de segurança (upload grande vai direto ao GCS) |
| <a id="adr-15"></a>ADR-15 | `TagSelector` esconde "Aviso de Conteúdo" na busca via `excludeCategories` | Vigente |
| <a id="adr-16"></a>ADR-16 | Listagem pública em duas consultas: página de obras, depois tags em lote | Vigente |
| <a id="adr-17"></a>ADR-17 | Sem `UNIQUE(file_hash)`; duplicata checada só na promoção | Vigente |
| <a id="adr-18"></a>ADR-18 | Promoção valida título e hash só contra obras públicas (privadas não se enxergam) | Vigente |
| <a id="adr-19"></a>ADR-19 | pdfjs-dist na v4, não na v5 | Vigente |
| <a id="adr-20"></a>ADR-20 | Worker do pdf.js servido pelo próprio frontend, sem CDN | Vigente |
| <a id="adr-21"></a>ADR-21 | Progresso por volume | Substituída pela ADR-44 para capítulos: progresso por capítulo (página, ou CFI e percentual no EPUB, lido a partir de 99%); volume só no legado |
| <a id="adr-22"></a>ADR-22 | Frontend e backend em serviços Cloud Run separados (escala a zero) | Vigente |
| <a id="adr-23"></a>ADR-23 | PostgreSQL 16 em Docker numa GCE e2-micro (free tier), acesso por Direct VPC egress | Vigente |
| <a id="adr-24"></a>ADR-24 | Upload em duas fases: URL assinada de PUT direto ao GCS, depois finalize no backend | Vigente (endurecida pela ADR-40) |
| <a id="adr-25"></a>ADR-25 | Hash do arquivo pelo MD5 dos metadados do GCS, sem baixar | Vigente |
| <a id="adr-26"></a>ADR-26 | Bucket em southamerica-east1, Cloud Run em us-east1 | Vigente |
| <a id="adr-27"></a>ADR-27 | Canvas do pdf.js escalado por `devicePixelRatio` (até 3) com escala mínima 1,5 | Vigente |
| <a id="adr-28"></a>ADR-28 | Rotação do refresh token a cada `/auth/refresh`, com fila no interceptor do front | Vigente |
| <a id="adr-29"></a>ADR-29 | E-mail pela API do Resend com domínio próprio, atrás da interface `EmailSender` | Vigente |
| <a id="adr-30"></a>ADR-30 | 2FA por TOTP; reset de senha exige o código quando o 2FA está ativo; sem recovery code | Vigente |
| <a id="adr-31"></a>ADR-31 | Clean Architecture por bounded context, quatro camadas; frontend leve | Vigente, na constituição (I) |
| <a id="adr-32"></a>ADR-32 | Domínio rico anotado com JPA, sem entidade de persistência separada | Vigente, na constituição (III) |
| <a id="adr-33"></a>ADR-33 | Exceção de domínio pura; HTTP só no `GlobalExceptionHandler` | Vigente, na constituição (III) |
| <a id="adr-34"></a>ADR-34 | `Work` raiz com `Volume` dentro; `Chapter`, `Tag`, `Rating`, `ReadingList` e `ReadingProgress` agregados próprios; recálculo de nota síncrono; VOs seletivos | Vigente |
| <a id="adr-35"></a>ADR-35 | RBAC na borda, posse na application por `actorId`; agregado nunca recebe `User` de outro contexto | Vigente, na constituição (II e IV) |
| <a id="adr-36"></a>ADR-36 | `Clock` injetável; `InactivityPolicy` (75 dias avisa, 90 desativa) como domain service puro | Vigente |
| <a id="adr-37"></a>ADR-37 | Repositórios Spring Data sem porta própria | Vigente, na constituição (I) |
| <a id="adr-38"></a>ADR-38 | Pirâmide de testes, AAA, `should_when`, rede de segurança antes de refatorar | Vigente, na constituição (V) |
| <a id="adr-39"></a>ADR-39 | Leitura cross-contexto só por use case público; sem SQL nativo nem JOIN entre contextos | Vigente, na constituição (II) |
| <a id="adr-40"></a>ADR-40 | `objectName` gerado pelo backend e vinculado à obra (`VolumeObjectName`, `ChapterObjectName`), prefixo `pending/` com lifecycle de 1 dia, `x-goog-content-length-range` no PUT, `VolumeFileCleaner` | Vigente |
| <a id="adr-41"></a>ADR-41 | Tokens persistidos só como SHA-256; refresh em cookie `httpOnly` `SameSite=Strict` `Path=/api/auth`; access token só em memória | Vigente |
| <a id="adr-42"></a>ADR-42 | "Esqueci a senha" publica no Pub/Sub e responde 200 na hora; push OIDC em `/internal/pubsub/password-reset` faz o trabalho; cadastro com e-mail existente responde 201 e avisa o dono | Vigente |
| <a id="adr-43"></a>ADR-43 | `X-Proxy-Secret` entre nginx e backend (`ProxySecretFilter`), exceto Pub/Sub, jobs e `/health` | Vigente |
| <a id="adr-44"></a>ADR-44 | Capítulo como unidade de leitura: agregado próprio, idioma, número opcional com rótulo, `PAGES` ou `FILE`, ciclo `PROCESSING → PUBLISHED / FAILED / UNPUBLISHED`, crédito do grupo | Vigente; volume convive até a migração do legado (#127) |
| <a id="adr-45"></a>ADR-45 | `Manga` renomeado para `Work` no código, banco (V27), API e rotas; o formato `MANGA` continua | Vigente |
| <a id="adr-47"></a>ADR-47 | Contexto `sourcing` (`TrackedWork`, `SourceChapter`, `SourceLease`), fala com `work` só por use case público | Vigente |
| <a id="adr-48"></a>ADR-48 | Cloud Run Job `buruna-ingest` disparado depois do commit; bytes originais das imagens; limites sobre os bytes lidos; idempotente; arquivo enviado apagado depois da extração | Vigente (imagem própria do Job pela ADR-54) |
| <a id="adr-49"></a>ADR-49 | Sync das obras acompanhadas a cada 3 h pelo Cloud Scheduler; avisos (removida, páginas trocadas, versão do grupo preferido); pausa automática depois de 8 falhas; e-mail diário aos ADMs | Vigente |
| <a id="adr-50"></a>ADR-50 | Idioma é atributo do capítulo (BCP 47); número único por obra e idioma; volume legado conta como pt-BR | Vigente |
| <a id="adr-51"></a>ADR-51 | Leitor de EPUB com foliate-js, isolado por CSP herdado (sem `'unsafe-inline'`, `blob:` só em frame, style e font) e limpeza do XHTML antes da carga | Vigente |
| <a id="adr-53"></a>ADR-53 | Unicidade em código novo na aplicação, com lock pessimista e consulta, sem `UNIQUE` | Vigente, na constituição (VII) |
| <a id="adr-54"></a>ADR-54 | Metadados pelo AniList, capítulos pelo manga-downloader (`list --json`); uma obra pode ter várias fontes; número que já existe na obra não é baixado de novo; imagem própria do Job com Chromium | Vigente; passos pendentes na #128 |
