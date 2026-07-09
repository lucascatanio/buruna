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
| **manga** | `com.buruna.manga` | Catálogo público, coleção privada, volumes, tags, submissão/promoção |
| **reading** | `com.buruna.reading` | Leitor (signed URLs), progresso de leitura, histórico |
| **engagement** | `com.buruna.engagement` | Avaliações (ratings) e lista de leitura |
| **admin** | `com.buruna.admin` | Casca administrativa — dashboard, jobs, revisão de submissões. Sem `domain`/`application` próprios; delega para os use cases públicos dos outros contextos |

Dois pacotes adicionais fora desse modelo, por não serem bounded contexts de domínio:

- `shared/` — infraestrutura cross-context: `StorageClient` (GCS/local), `EmailSender`,
  `Clock` injetável, `GlobalExceptionHandler`, config de segurança.
- `feedback/` — módulo utilitário isolado (`POST /feedback`), sem lógica de domínio
  suficiente para justificar camadas próprias.

> `manga` e `admin` ainda têm pacotes `controller/`/`service/` remanescentes do padrão
> antigo convivendo com `web/`/`application/` — rename pendente, ver
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
- **`application/`** — casos de uso (um por intenção, ex.: `PromoteMangaUseCase`,
  `RunInactivityUseCase`), DTOs, fronteira transacional. Consome `domain` + portas
  (`StorageClient`, `EmailSender`, repositórios).
- **`persistence/`** — interfaces Spring Data JPA. Repositórios não têm ports/abstrações
  customizadas — decisão deliberada, ver [ADR-37](adr/ADR-37-repositorios-spring-data-sem-ports.md).
- **`web/`** — controllers finos: extraem `actorId` (via `@AuthenticationPrincipal`) e
  `@PreAuthorize`, delegam ao use case, nunca contêm lógica de negócio.

Regra completa de camadas e proporcionalidade (contextos quase-CRUD podem ter um único
application service): [ADR-31](adr/ADR-31-camadas-clean-arch-pragmaticas.md).
Fronteiras de agregado (`Manga` raiz com `Volume`, um agregado por caso público/privado):
[ADR-34](adr/ADR-34-agregados-e-fronteiras.md).

### Cross-context

- Um contexto **nunca** importa `domain`/`persistence` de outro — só o `application`
  (use case público) do outro contexto.
- Referências cross-contexto são por **UUID** (`actorId`, `ownerId`), nunca a entidade
  de outro contexto.
- `@Query(nativeQuery=true)` e `JOIN` entre tabelas de contextos diferentes são
  proibidos — passe por um use case público. Ver [ADR-39](adr/ADR-39-cross-context-read-via-use-case.md)
  para o caso concreto que motivou a regra (`reading` lendo `volumes` de `manga`).
- RBAC (`@PreAuthorize`) na borda; ownership (posse) verificada na `application` via
  `actorId`. Autorização unificada e fim do acoplamento `identity ↔ manga`:
  [ADR-35](adr/ADR-35-autorizacao-unificada.md).

### Exceções

Exceções de domínio são puras (`DomainErrorType`, sem `HttpStatus`); a tradução para
resposta HTTP acontece só no `GlobalExceptionHandler` (`shared/exception/`), retornando
`ErrorResponse {status, error, message, path, timestamp}`. `LegacyHttpDomainException`
é legado em remoção — não crie novos usos. Ver [ADR-33](adr/ADR-33-excecoes-dominio-sem-httpstatus.md).

## 4. ArchUnit como guarda de arquitetura

`ArchitectureTest` (`backend/src/test/java/com/buruna/architecture/ArchitectureTest.java`)
falha o build (`./mvnw clean test`) se a fronteira for violada. Três regras:

1. **`domainAndApplication_shouldNotImportInternalsOfOtherContexts`** — nenhuma classe em
   `domain/`/`application/` de um contexto migrado pode depender de `domain/`/`persistence/`
   de outro.
2. **`adminServiceLayer_shouldOnlyUseApplicationLayerOfOtherContexts`** — guard específico
   para `admin` (que não tem `domain`/`application` próprios): `admin.service` só pode
   consumir `application` de outros contextos.
3. **`persistenceLayer_shouldNotUseNativeQueries`** — detecta `@Query(nativeQuery=true)`
   nas camadas `persistence` de contextos migrados.

O que nenhum guard cobre (JPQL referenciando entidade de outro contexto por nome de
string) é review-only — ver a seção "Guard de arquitetura" em
[ADR-39](adr/ADR-39-cross-context-read-via-use-case.md).

## 5. Frontend leve

Sem Clean Architecture no frontend — o custo não se paga para uma SPA
(ver [ADR-31](adr/ADR-31-camadas-clean-arch-pragmaticas.md) §pragmatismo). Apenas duas
convenções:

- `frontend/src/api/` — uma chamada Axios tipada por contexto (`identityApi.ts`,
  `mangaApi.ts`, `privateMangaApi.ts`, `readingApi.ts`, `engagementApi.ts`, `adminApi.ts`,
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
   │                      │                              │──── e-mail (@Async) ──────►│
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

### 6.3 Leitura de mangá

```
BROWSER                              BACKEND (reading)                    GCS
  │── GET /mangas?page=0&size=20 ───►│ (manga.application.CatalogQueryUseCase / FindPublicMangaUseCase)
  │◄── lista de mangás ──────────────│
  │── GET /mangas/{slugOrId} ────────►│
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

Controller `reading.web.ReaderController`, serviço `reading.application.ReadingService`.
Se a signed URL expirar (403 do GCS), o frontend pede uma nova via o mesmo endpoint.

### 6.4 Upload de volume público (duas fases)

Use cases: `GeneratePublicVolumeUploadUrlUseCase` (fase 1 — gera Signed URL de PUT) e
`FinalizePublicVolumeUseCase` (fase 3 — lê metadados do blob via `blob.getMd5()`,
persiste `Volume`). Controller `manga.controller.VolumeController`
(`POST /mangas/{id}/volumes/upload-url`, `POST /mangas/{id}/volumes/finalize`). O
backend nunca toca os bytes do arquivo — ver [ADR-24](adr/ADR-24-upload-direto-gcs-signed-url.md)
e [ADR-25](adr/ADR-25-hash-blob-getmd5-gcs.md).

### 6.5 Upload privado + submissão/promoção

Controller `manga.controller.PrivateMangaController` (`/my/mangas`). Use cases:
`CreatePrivateMangaUseCase` → `GenerateVolumeUploadUrlUseCase` → `FinalizeVolumeUseCase`
para criar mangá + volume na coleção privada; `SubmitForApprovalUseCase` para submeter
à revisão (`AdminSubmissionController`, `ReviewSubmissionUseCase` no approve/reject);
`PromoteMangaUseCase` para promoção direta (`COLLABORATOR`+) sem revisão. Os dois
caminhos (promote × submit→approve) coexistem por decisão de domínio.

Validação de unicidade no promote/aprovação é só contra mangás públicos — ver
[ADR-17](adr/ADR-17-remocao-unique-file-hash-v15.md) e [ADR-18](adr/ADR-18-promote-valida-unicidade-mangas-publicos.md).

> Estado atual do enum `MangaSubmissionStatus` (só `PENDING`/`REJECTED`, sem `APPROVED`)
> é uma assimetria de domínio conhecida — ver [`docs/glossario-dominio.md`](glossario-dominio.md)
> §3 e a issue em [`docs/BACKLOG.md`](BACKLOG.md).

### 6.6 Inatividade automática

Use case público: `identity.application.admin.RunInactivityUseCase`, disparado por
`admin.controller.JobController` (`POST /admin/jobs/inactivity`, autenticado via
`X-Job-Secret`) e também por `@Scheduled` interno como fallback. A política de "quantos
dias até aviso/desativação" é domínio puro (`InactivityPolicy`) testável sem framework,
usando `java.time.Clock` injetável — ver [ADR-36](adr/ADR-36-clock-injetavel-e-inactivity-policy.md).
Usuários `ACTIVE` são processados em páginas de 50 via `Pageable`.

Para deletar a coleção privada de um usuário desativado, o `identity` chama o use case
público `manga.application.maintenance.DeletePrivateCollectionForUserUseCase` — exemplo
concreto de cross-context via `application`, não via acesso direto a `persistence`.
