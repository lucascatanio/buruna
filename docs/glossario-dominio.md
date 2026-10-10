# Glossário de Domínio — Burūna

> Linguagem ubíqua extraída do código real (agregados, Value Objects, enums, exceções de
> domínio e use cases), pós-refatoração Clean Architecture + DDD (Epics 0–6). Fonte da
> verdade: `backend/src/main/java/com/buruna/{identity,work,reading,engagement,admin}`.
> Ordenado por contexto; termos cross-contexto no topo.

---

## 1. Bounded Contexts

| Termo | Contexto | Definição |
|---|---|---|
| **Identity & Access** (`identity`) | — | Fusão de `auth`+`user`. Dono do agregado `User`, autenticação (JWT+refresh, TOTP), aprovação e política de inatividade. |
| **Catalog & Collection** (`work`) | — | Core domain. Um único agregado `Work` (VO `isPublic`) cobre catálogo público e coleção privada, mais `Volume`, `Tag`/`TagCategory`. |
| **Reading** (`reading`) | — | Supporting. Progresso de leitura, histórico e URL assinada de acesso a volume. |
| **Engagement** (`engagement`) | — | Supporting. Avaliações (`Rating`) e lista de leitura (`ReadingList`). |
| **Administration** (`admin`) | — | Casca/orquestração, sem domínio próprio — controllers chamam use cases públicos dos outros contextos. |

---

## 2. Contexto `identity`

| Termo | Definição |
|---|---|
| **User** (agregado raiz) | Usuário da plataforma. Mutação só por método de negócio: `approve()`, `reject()`, `deactivate()`, `changeStatus()`, `anonymize()`, `changeRole()`, `changeQuota()`, `changePassword()`, `assignAvatar()`, `recordLogin()`, `startTotpSetup()`/`enableTotp()`/`disableTotp()`. |
| **UserStatus** (enum) | `PENDING` (aguardando aprovação) → `ACTIVE` (aprovado, uso normal) → `INACTIVE` (desativado por inatividade). `DELETED`: conta removida pelo dono e anonimizada (`anonymize()`); estado final, `changeStatus()` recusa sair dele. |
| **Role** (enum) | `READER` (leitor comum) < `COLLABORATOR` (pode ter coleção privada, promover mangá) < `ADMIN` (aprova usuários, revisa submissões, gerencia catálogo). |
| **Email** (VO) | E-mail validado por regex (`^[^@\s]+@[^@\s]+\.[^@\s]+$`) na construção; lança `InvalidEmailException` se inválido. |
| **Username** (VO) | Nome de usuário validado na construção; lança `InvalidUsernameException` se inválido. |
| **Quota** (VO, `identity`) | Cota de armazenamento em GB (`BigDecimal`), com `canFit(usedBytes, additionalBytes)`/`remaining(usedBytes)` em bytes (1 GiB = 1024³). Persistida como atributo do `User`; passada como primitivo para o contexto `work` (ADR-35) — **não** é o mesmo VO de `work.domain.Quota` (ver §3). |
| **RefreshToken** | Token de renovação de sessão; rotacionado a cada uso (R5). |
| **PasswordResetToken** | Token de reset de senha, com expiração. |
| **InactivityPolicy** (domain service puro) | `decide(lastAccessAt, now) → NONE\|WARN\|DEACTIVATE`. Testável sem Spring. Limiares: **> 75 dias** sem acesso → `WARN`; **> 90 dias** → `DEACTIVATE` (exatamente 75/90 dias → decisão anterior, sem ação). `lastAccessAt` nulo → `NONE`. |
| **InactivityDecision** (enum) | Resultado da `InactivityPolicy`: `NONE`, `WARN`, `DEACTIVATE`. |
| **2FA / TOTP** | Segundo fator via TOTP; `startTotpSetup`/`enableTotp`/`disableTotp` no agregado `User`. |
| **Captcha** | hCaptcha na borda de registro/login; desligado em dev quando `app.hcaptcha.secret` vazio. |

---

## 3. Contexto `work` (Catalog & Collection — core domain)

| Termo | Definição |
|---|---|
| **Work** (agregado raiz) | Obra. Um único agregado cobre **público** (catálogo) e **privado** (coleção do dono), distinguido por `isPublic`. Contém `Volume` por composição (cascade/orphanRemoval). Métodos de negócio: `submitForApproval()`, `approve(reviewerId)`, `reject(reviewerId, reason)`, `promoteToPublic()`. |
| **Volume** (entidade interna) | Arquivo (PDF ou imagem, conforme `WorkFormat`) de um número dentro de um `Work`. Identidade por (work, `VolumeNumber`); só mutável através do agregado `Work` (invariante de número único). |
| **Chapter** (agregado raiz) | Capítulo: a unidade de leitura (ADR-44). Referencia a `Work` por `workId`, sem carregá-la. Tem idioma, número opcional ou rótulo, tipo (`ChapterKind`) e status (`ChapterStatus`). Métodos: `register()` (nasce `PROCESSING`), `publishPages()` (páginas de 1 a N, sem buraco), `fail()`. Número único por (obra, idioma) é regra do `RegisterChapterUseCase`, sob lock na obra (ADR-53). Convive com `Volume` até a migração do legado. |
| **ChapterPage** (embutido) | Página de um capítulo de imagens: posição (a partir de 1), objeto no storage, variante opcional de economia de dados, largura e altura (para o leitor reservar espaço). |
| **ChapterKind** (enum) | `PAGES` (imagens por página: mangá, manhwa) e `FILE` (arquivo único: livro em PDF ou EPUB). |
| **ChapterStatus** (enum) | `PROCESSING` (conteúdo sendo extraído ou baixado) → `PUBLISHED` (visível) ou `FAILED` (não ocupa o número; dá para tentar de novo). `UNPUBLISHED`: tirado do ar sem apagar. |
| **ChapterNumber** (VO) | Número do capítulo: decimal >= 0 com até 2 casas (10.5 existe). 10 e 10.0 são o mesmo número. |
| **Language** (VO) | Idioma do capítulo como tag BCP 47 normalizada (`pt-br` → `pt-BR`). Códigos próprios de uma fonte externa são traduzidos por quem integra a fonte. |
| **Owner** | Usuário dono de um `Work` privado (referenciado por UUID — nunca a entidade `User`, ADR-35/ADR-39). |
| **Promote** | `COLLABORATOR`+ move o **próprio** `Work` privado direto para público via `promoteToPublic()` — caminho direto, sem revisão. |
| **Submission** | Qualquer usuário `ACTIVE` pede publicação via `submitForApproval()`; um `ADMIN` decide com `approve()`/`reject()`. Dois caminhos (promote × submit→approve) coexistem por decisão de domínio. |
| **WorkSubmissionStatus** (enum) | `PENDING` (aguardando revisão), `APPROVED` (aprovado por um `ADMIN`; a obra vira pública) e `REJECTED` (recusado, com `rejectionReason`). `null` = sem submissão: nunca submetido, ou promovido direto por `promoteToPublic()`, que encerra uma submissão aberta sem status. Obra pública tem sempre `APPROVED` ou `null`. `APPROVED` entrou na V24; a V25 marcou as aprovações anteriores (público + `reviewedAt` preenchido). |
| **WorkStatusOrigin** (enum) | Status da obra na fonte original: `ONGOING`, `COMPLETED`, `HIATUS`, `CANCELLED`. |
| **WorkStatusSite** (enum) | Status da publicação **no Burūna**: `COMPLETE` (todos os volumes disponíveis), `INCOMPLETE`. |
| **WorkFormat** (enum) | `MANGA`, `MANHWA`, `MANHUA`, `WEBTOON`, `ONESHOT`, `LIVRO`. |
| **Tag** / **TagCategory** | Reference data administrada por `ADMIN`; `Work` referencia `Tag` por id. |
| **Slug** (VO) | Identificador amigável de URL do `Work`, normalizado a partir do título; unicidade resolvida via callback do use case (`resolveSlugConflict`), sem domain service à parte. |
| **VolumeNumber** (VO) | Número de volume validado (`InvalidVolumeNumberException` se inválido); garante unicidade dentro do agregado `Work`. |
| **FileHash** (VO) | Hash do arquivo de um volume; usado para detectar duplicidade entre público e privado (`PublicVolumeConflictException`). |
| **Quota** (VO, `work`) | Cota de coleção privada em bytes: `Quota.of(limitGb, usedBytes)`, com `canFit(additionalBytes)`/`remaining()`. Limite chega como primitivo (`BigDecimal`) vindo do `User` de `identity`; consumo é somado das tabelas do próprio contexto `work`. Puro, testável em JUnit. Distinto do VO homônimo em `identity` (§2) — cada contexto modela sua própria cota, sem importar o domínio do outro. |
| **Signed URL** | URL temporária (GCS) para upload/leitura sem passar pelo backend; expira em 30 min. |
| **Upload em 2 fases** | `GenerateVolumeUploadUrlUseCase` (URL assinada de PUT) → `FinalizeVolumeUseCase` (confirma e persiste metadados). Existe em par público/privado. |
| **Catálogo público** | Mangás com `isPublic = true`; listagem paginada, busca/filtro por tags (two-step). |
| **Coleção privada** | Mangás com `isPublic = false`, visíveis só ao `Owner`; sujeita a `Quota` e candidata a `promote`/`submit`. |

**Exceções de domínio notáveis:** `WorkAlreadyPublicException`, `WorkAlreadySubmittedException`, `SubmissionNotPendingException`, `PublicTitleConflictException`, `PublicVolumeConflictException`, `DuplicateVolumeException`, `InsufficientStorageQuotaException`.

**Use cases (recorte, `work/application/`):** `CreatePrivateWorkUseCase`, `CreatePublicWorkUseCase`, `CatalogQueryUseCase`, `SubmitForApprovalUseCase`, `ReviewSubmissionUseCase`, `PromoteWorkUseCase`, `GenerateVolumeUploadUrlUseCase`/`FinalizeVolumeUseCase` (privado) e equivalentes públicos, `DeletePrivateCollectionForUserUseCase` (em `application/maintenance`, consumido pelo job de inatividade de `identity` sem acoplamento reverso — ADR-35).

---

## 4. Contexto `reading` (simplificado)

| Termo | Definição |
|---|---|
| **ReadingProgress** (agregado raiz) | Página atual (`PageNumber`) de um usuário num `Volume`; um registro por (user, volume), upsert. |
| **ReadingHistory** | Registro de acesso de leitura (view count / histórico), gravado no mesmo fluxo da URL assinada. |
| **Signed URL de leitura** | `GET /reader/{volumeId}/url`; expira em 30 min; incrementa `view_count` e grava `ReadingHistory` no mesmo fluxo (R2). |

---

## 5. Contexto `engagement` (simplificado)

| Termo | Definição |
|---|---|
| **Rating** (agregado raiz) | Avaliação 1–5 de um usuário sobre uma `Work` pública; um por (user, work); alimenta `avgRating`/`ratingCount` do `Work` (recálculo síncrono, cross-aggregate — ADR-34). |
| **Score** (VO) | Nota 1–5; lança `ScoreOutOfRangeException` fora do intervalo. |
| **ReadingList** (agregado raiz) | Item de lista de leitura de um usuário para um `Work`; um por (user, work). |
| **ReadingStatus** (enum) | `WANT_TO_READ`, `READING`, `COMPLETED`, `DROPPED`. |

---

## 6. Conceitos transversais

| Termo | Definição |
|---|---|
| **actorId** | UUID do usuário autenticado, passado como primitivo para a `application` — nunca a entidade `User` de `identity` cruza para outro contexto (ADR-35/ADR-39). |
| **Ownership** | Regra "o recurso pertence ao actorId" verificada na `application` (não no `@PreAuthorize`, que cobre só RBAC). |
| **RBAC na borda** | `@PreAuthorize` no controller decide **papel** (`Role`); a `application` decide **posse** (ownership) via query/`actorId`. |
| **Exceção de domínio pura** | Estende `DomainException` com um `DomainErrorType`, sem `HttpStatus` (ADR-33); tradução para HTTP só no `GlobalExceptionHandler`. O padrão antigo (`LegacyHttpDomainException`) foi removido no Epic 6. |
| **Domain service** | Só quando a lógica não pertence a uma entidade e precisa de I/O (ex.: `QuotaService`, `SlugAllocator` em `work/application` — a parte pura fica no VO/policy, a parte com repositório fica na `application`). |

---

## 7. Políticas de negócio nomeadas

| Política | Regra |
|---|---|
| **Inatividade** | > 75 dias sem acesso → aviso por e-mail; > 90 dias → desativação (`UserStatus.INACTIVE`) + apagar coleção privada (`DeletePrivateCollectionForUserUseCase`). Ver `InactivityPolicy` (§2). |
| **Promoção direta** | `COLLABORATOR`+ pode promover o próprio privado sem revisão (`promoteToPublic()`). |
| **Submissão com revisão** | Qualquer `ACTIVE` pode submeter; só `ADMIN` aprova/rejeita. Ambos os caminhos coexistem (decisão travada na Fase 1, ver `docs/refactor/01-analise-estado-atual.md` §D7/R6). |
| **Conflito de publicação** | Promoção/aprovação falha se já existe título público igual (`PublicTitleConflictException`) ou hash de arquivo já público (`PublicVolumeConflictException`). |
