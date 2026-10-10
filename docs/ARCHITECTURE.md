# Arquitetura

> Documento de referência para quem vai trabalhar no projeto sem contexto prévio.
> Descreve o estado **atual** do backend (Clean Architecture por bounded context) e os
> fluxos de usuário principais. Para infraestrutura de deploy, veja
> [DEPLOYMENT.md](DEPLOYMENT.md); para modelo de dados, [DATABASE.md](DATABASE.md); para
> segurança, [SECURITY.md](../SECURITY.md). As decisões de design (o "porquê" de cada
> escolha) vivem em [`docs/adr/`](adr/) — este documento aponta para elas em vez de
> repetir o conteúdo.

## 1. Visão geral

O backend é um **monolito modular** (não microsserviços — ver [ADR-01](adr/ADR-01-monolito-modular-spring-boot.md)),
organizado em **Clean Architecture por bounded context**. Cada contexto de negócio tem
suas próprias camadas `domain/ → application/ → persistence/ → web/`, com uma regra de
dependência guardada por teste automatizado (`ArchitectureTest`, ArchUnit) — não apenas
por convenção de code review.

O frontend é uma SPA React sem Clean Architecture própria: apenas uma camada leve
`api/` (chamadas HTTP tipadas) + `types/` (contratos TypeScript), ver §5.

## 2. Os bounded contexts

| Contexto | Pacote | Responsabilidade |
|---|---|---|
| **identity** | `com.buruna.identity` | Autenticação (JWT + refresh + 2FA), cadastro/aprovação, gestão de usuários (fusão de auth+user) |
| **work** | `com.buruna.work` | Catálogo público, coleção privada, volumes, tags, submissão/promoção |
| **reading** | `com.buruna.reading` | Leitor (signed URLs), progresso de leitura, histórico |
| **engagement** | `com.buruna.engagement` | Avaliações (ratings) e lista de leitura |
| **admin** | `com.buruna.admin` | Casca administrativa — dashboard, jobs, revisão de submissões. Sem `domain` próprio; a `application` (`DashboardService`) só orquestra os use cases públicos dos outros contextos |

Dois pacotes adicionais fora desse modelo, por não serem bounded contexts de domínio:

- `shared/` — infraestrutura cross-context: `StorageClient` (GCS/local), `EmailSender`,
  `Clock` injetável, `GlobalExceptionHandler`, config de segurança.
- `feedback/` — módulo utilitário isolado (`POST /feedback`), sem lógica de domínio
  suficiente para justificar camadas próprias.

> DTO que um use case recebe ou devolve mora na `application/` do contexto; na `web/` só
> fica o que apenas o controller usa (ADR-31). `identity`, `engagement` e `reading` ainda
> importam DTOs da `web/` na `application/` — alinhamento pendente, ver
> [`docs/BACKLOG.md`](BACKLOG.md).

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
  entidade de persistência — ver [ADR-32](adr/ADR-32-dominio-rico-anotado-jpa.md).
- **`application/`** — casos de uso (um por intenção, ex.: `PromoteWorkUseCase`,
  `RunInactivityUseCase`), DTOs, fronteira transacional. Consome `domain` + portas
  (`StorageClient`, `EmailSender`, repositórios).
- **`persistence/`** — interfaces Spring Data JPA. Repositórios não têm ports/abstrações
  customizadas — decisão deliberada, ver [ADR-37](adr/ADR-37-repositorios-spring-data-sem-ports.md).
- **`web/`** — controllers finos: extraem `actorId` (via `@AuthenticationPrincipal`) e
  `@PreAuthorize`, delegam ao use case, nunca contêm lógica de negócio.

Regra completa de camadas e proporcionalidade (contextos quase-CRUD podem ter um único
application service): [ADR-31](adr/ADR-31-camadas-clean-arch-pragmaticas.md).
Fronteiras de agregado (`Work` raiz com `Volume`, um agregado por caso público/privado):
[ADR-34](adr/ADR-34-agregados-e-fronteiras.md).

### Cross-context

- Um contexto **nunca** importa `domain`/`persistence` de outro — só o `application`
  (use case público) do outro contexto.
- Referências cross-contexto são por **UUID** (`actorId`, `ownerId`), nunca a entidade
  de outro contexto.
- `@Query(nativeQuery=true)` e `JOIN` entre tabelas de contextos diferentes são
  proibidos — passe por um use case público. Ver [ADR-39](adr/ADR-39-cross-context-read-via-use-case.md)
  para o caso concreto que motivou a regra (`reading` lendo `volumes` de `work`).
- RBAC (`@PreAuthorize`) na borda; ownership (posse) verificada na `application` via
  `actorId`. Autorização unificada e fim do acoplamento `identity ↔ work`:
  [ADR-35](adr/ADR-35-autorizacao-unificada.md).

### Exceções

Exceções de domínio são puras (`DomainErrorType`, sem `HttpStatus`); a tradução para
resposta HTTP acontece só no `GlobalExceptionHandler` (`shared/exception/`), retornando
`ErrorResponse {status, error, message, path, timestamp}`. A `LegacyHttpDomainException`
do padrão antigo já foi removida por completo no Epic 6 — nenhuma exceção carrega
`HttpStatus` hoje. Ver [ADR-33](adr/ADR-33-excecoes-dominio-sem-httpstatus.md).

## 4. ArchUnit como guarda de arquitetura

`ArchitectureTest` (`backend/src/test/java/com/buruna/architecture/ArchitectureTest.java`)
falha o build (`./mvnw clean test`) se a fronteira for violada. Três regras:

1. **`domainAndApplication_shouldNotImportInternalsOfOtherContexts`** — nenhuma classe em
   `domain/`/`application/` de um contexto migrado pode depender de `domain/`/`persistence/`
   de outro.
   `admin` está entre os contextos migrados: sua `application` só consome `application`
   de outros contextos.
2. **`domainAndApplication_shouldNotDependOnWebLayer`** — `domain/`/`application/` não
   dependem da `web/` do próprio contexto (seta do ADR-31). Por ora vale para `work` e
   `admin`; os demais entram quando forem alinhados.
3. **`persistenceLayer_shouldNotUseNativeQueries`** — detecta `@Query(nativeQuery=true)`
   nas camadas `persistence` de contextos migrados.

Nenhuma regra restringe o que a camada `web/` importa, de propósito: um controller recebe
`identity.domain.User` via `@AuthenticationPrincipal` e extrai `user.getId()` antes de
delegar (ex.: `engagement.web.RatingController`). Esse import cross-contexto na `web/` é
o padrão aceito, não uma violação.

O que nenhum guard cobre (JPQL referenciando entidade de outro contexto por nome de
string) é review-only — ver a seção "Guard de arquitetura" em
[ADR-39](adr/ADR-39-cross-context-read-via-use-case.md).

## 5. Frontend leve

Sem Clean Architecture no frontend — o custo não se paga para uma SPA
(ver [ADR-31](adr/ADR-31-camadas-clean-arch-pragmaticas.md) §pragmatismo). Apenas duas
convenções:

- `frontend/src/api/` — uma chamada Axios tipada por contexto (`identityApi.ts`,
  `workApi.ts`, `privateWorkApi.ts`, `chapterApi.ts`, `readingApi.ts`, `engagementApi.ts`, `adminApi.ts`,
  `feedbackApi.ts`).
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

As rotas antigas `/mangas...` continuam aceitas como alias por uma versão (para abas abertas com
o front antigo) e serão removidas na seguinte — ver [ADR-45](adr/ADR-45-renomear-manga-para-work.md).

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
histórico de novo. Isolamento do EPUB: [ADR-51](adr/ADR-51-leitor-epub-foliate.md).

### 6.4 Upload de volume público (duas fases)

Use cases: `GeneratePublicVolumeUploadUrlUseCase` (fase 1 — gera Signed URL de PUT para
`pending/volumes/{workId}/{uuid}.pdf`, um caminho VINCULADO à obra via o Value Object
`work.domain.VolumeObjectName`) e `FinalizePublicVolumeUseCase` (fase 2 — valida que o
`objectName` recebido é um pendente da PRÓPRIA obra, lê metadados do blob via
`blob.getMd5()`, move o objeto para `volumes/{workId}/{uuid}.pdf` e persiste `Volume`).
Controller `work.web.VolumeController`
(`POST /works/{id}/volumes/upload-url`, `POST /works/{id}/volumes/finalize`). O
backend nunca toca os bytes do arquivo — ver [ADR-24](adr/ADR-24-upload-direto-gcs-signed-url.md),
[ADR-25](adr/ADR-25-hash-blob-getmd5-gcs.md) e [ADR-40](adr/ADR-40-objectname-vinculado-ao-manga.md)
(objectName vinculado à obra + prefixo `pending/`).

### 6.4.1 Upload de capítulo (CBZ, CBR ou PDF)

Mesmo desenho em duas fases do volume, com a extração das páginas fora da requisição
([ADR-48](adr/ADR-48-ingest-em-cloud-run-job.md)). Rotas `/my/works/{id}/chapters/...`
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

### 6.5 Upload privado + submissão/promoção

Controller `work.web.PrivateWorkController` (`/my/works`). Use cases:
`CreatePrivateWorkUseCase` → `GenerateVolumeUploadUrlUseCase` → `FinalizeVolumeUseCase`
para criar obra + volume na coleção privada; `SubmitForApprovalUseCase` para submeter
à revisão (`AdminSubmissionController`, `ReviewSubmissionUseCase` no approve/reject);
`PromoteWorkUseCase` para promoção direta (`COLLABORATOR`+) sem revisão. Os dois
caminhos (promote × submit→approve) coexistem por decisão de domínio.

Validação de unicidade no promote/aprovação é só contra obras públicas — ver
[ADR-17](adr/ADR-17-remocao-unique-file-hash-v15.md) e [ADR-18](adr/ADR-18-promote-valida-unicidade-mangas-publicos.md).

> `WorkSubmissionStatus` é `PENDING` → `APPROVED` | `REJECTED`; a promoção direta encerra uma
> submissão aberta sem status — ver [`docs/glossario-dominio.md`](glossario-dominio.md) §3.

### 6.6 Inatividade automática

Use case público: `identity.application.admin.RunInactivityUseCase`, disparado por
`admin.web.JobController` (`POST /admin/jobs/inactivity`, autenticado via
`X-Job-Secret`), chamado diariamente pelo Cloud Scheduler (ADR-42). A política de "quantos
dias até aviso/desativação" é domínio puro (`InactivityPolicy`) testável sem framework,
usando `java.time.Clock` injetável — ver [ADR-36](adr/ADR-36-clock-injetavel-e-inactivity-policy.md).
Usuários `ACTIVE` são processados em páginas de 50 via `Pageable`.

Para deletar a coleção privada de um usuário desativado, o `identity` chama o use case
público `work.application.maintenance.DeletePrivateCollectionForUserUseCase` — exemplo
concreto de cross-context via `application`, não via acesso direto a `persistence`.

### 6.6.1 Arquivos órfãos de volumes

O delete de volume apaga a linha na transação e o arquivo do GCS depois, best-effort
([ADR-24](adr/ADR-24-upload-direto-gcs-signed-url.md)): se o GCS falha, o arquivo fica em `volumes/` sem linha em `volumes`. O use
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
