# Fase 1 — Análise do Estado Atual (Burūna)

> 📋 **Documento de processo — diário da migração, não documentação viva.** Registra o
> diagnóstico do estado do código no momento em que a refatoração começou (2026-06-01).
> As referências a `docs/buruna_architecture.md` abaixo apontavam para o caminho antigo
> na época; o arquivo foi movido para [`docs/legacy/buruna_architecture.md`](../legacy/buruna_architecture.md).
> Para o estado atual do projeto, ver [CLAUDE.md](../../CLAUDE.md) e [`docs/`](../).

> **Tipo:** Diagnóstico (somente leitura — nenhum código foi alterado).
> **Data:** 2026-06-01
> **Fonte da verdade:** o código (`backend/src/main/java`, `frontend/src`, migrations Flyway). A documentação em `docs/buruna_architecture.md` é tratada como histórico e está parcialmente desatualizada (ver §2).
> **Método:** varredura completa do repositório — 140 arquivos Java, 34 `.tsx`, 20 migrations, 7 scripts de teste bash.

---

## 1. Mapa do código como ele é hoje

### 1.1 Estrutura real do backend (`com.buruna`)

Pacote por feature/módulo. Cada módulo segue `controller / service / repository / domain / dto / exception`.

| Pacote (módulo) | Responsabilidade | Controllers | Services | Entidades (`domain`) |
|---|---|---|---|---|
| `auth` | Login, registro, JWT, refresh token, 2FA TOTP, reset de senha, captcha | `AuthController` | `AuthService` (313 l), `TokenService`, `TotpService`, `CaptchaService` | `RefreshToken`, `PasswordResetToken` |
| `user` | Usuários, aprovação, roles, status, cota, job de inatividade | `AdminUserController` | `UserService`, `InactivityJob` | `User` (+ enums `Role`, `UserStatus`) |
| `manga` | Catálogo público, coleção privada, volumes, tags, cota, submissão, promoção | `MangaController`, `PrivateMangaController`, `VolumeController`, `TagController` | `MangaService`, `PrivateMangaService` (350 l), `VolumeService`, `TagService`, `StorageQuotaService`, `MangaResponseMapper` | `Manga`, `Volume`, `Tag`, `TagCategory` (+ enums de formato/status) |
| `reader` | Leitura, URL assinada de PDF, progresso, histórico | `ReaderController` | `ReaderService` | `ReadingProgress`, `ReadingHistory` |
| `engagement` | Lista de leitura + avaliações (ratings) | `RatingController`, `ReadingListController` | `RatingService`, `ReadingListService` | `Rating`, `ReadingList` (+ enum `ReadingStatus`) |
| `feedback` | Envio de feedback do usuário | `FeedbackController` | — (controller usa `EmailService`) | — |
| `admin` | Dashboard, gatilho de jobs, revisão de submissões | `DashboardController`, `JobController`, `AdminSubmissionController` | `DashboardService` | — (reusa services de outros módulos) |
| `infra` | Cross-cutting: segurança, storage, e-mail, exceções, config, conversores | `HealthController`, `LocalStorageController` | `EmailService`, `EmailSender`/`ResendEmailSender`, `StorageClient`/`Gcs`/`Local`, filtros | — |

**Camadas hoje (de fato):** `Controller (thin) → Service (gordo, regra de negócio) → Repository (Spring Data JPA) → Entidade JPA`. DTOs são `record`s. O mapeamento entidade→DTO é feito **inline** dentro dos services (com `MangaResponseMapper` cobrindo só parte do caso público).

**11 entidades JPA, 20 migrations** (`V1`…`V20`).

### 1.2 Pontos de arquitetura que já estão *bons* (não mexer sem motivo)

- **`StorageClient`** (interface) com `GcsStorageClient` + `LocalStorageClient`: abstração **legítima** (DIP bem aplicado) — permite rodar local sem GCS. Mantém o padrão na arquitetura-alvo.
- **`EmailSender`** (interface) + `ResendEmailSender`: idem, abstração justificada (ADR-29).
- **Controllers são finos**: delegam ao service e cuidam só de HTTP status/binding. Bom ponto de partida.
- **DTOs como `record`** e validação via `@Valid` na borda. Convenção consistente.
- **Tratamento de erro centralizado** em `GlobalExceptionHandler` (`@RestControllerAdvice`) com `ErrorResponse` padronizado.

### 1.3 Estrutura real do frontend (`frontend/src`)

React **19** + TypeScript + Vite + Tailwind v4 + shadcn/ui (radix-ui) + **zustand** (`store/authStore.ts`) + react-router-dom **7** + axios (interceptor com fila de refresh em `lib/axios.ts`) + pdfjs-dist **v4**.

- `pages/` (22 telas, incl. `pages/admin/`), `components/` (layouts, `ProtectedRoute`, `TagSelector`, `FeedbackDialog`, `ui/`), `lib/` (`axios`, `signedUrlCache`, `utils`), `store/` (auth).
- Sem camada de domínio/serviço de API formal: as telas chamam `axios` direto. Aceitável para o tamanho atual, mas é candidato a uma fina camada `api/` na Fase 2 (proporcionalidade — ver §6).

### 1.4 Inventário de endpoints (referência rápida)

```
auth:        POST /auth/register|login|refresh|logout  DELETE /auth/account
             GET  /auth/2fa/status  POST /auth/2fa/{setup,verify,disable,authenticate}
             POST /auth/password/forgot  GET /auth/password/reset-info  POST /auth/password/reset
mangas:      GET /mangas  GET /mangas/{slugOrId}  POST /mangas  PUT /mangas/{id}  DELETE /mangas/{id}
volumes:     GET /mangas/{id}/volumes  POST .../upload-url  POST .../finalize  DELETE .../{volumeId}
my/mangas:   GET (lista) GET /{id} GET /quota POST PUT/{id} DELETE/{id}
             POST /{id}/volumes/{upload-url,finalize}  DELETE /{id}/volumes/{volumeId}
             POST /{id}/submit  POST /{id}/promote
tags:        GET /tags  GET /tag-categories  POST /tag-categories  POST /tags  PUT/DELETE /tags/{id}
reader:      GET /reader/{volumeId}/url  POST /reader/{volumeId}/progress
             GET /reader/progress/{mangaId}  GET /reader/{volumeId}/progress
             GET /reader/progress/batch  GET /reader/history
engagement:  GET/PUT/DELETE /reading-list  GET/POST/PUT/DELETE /mangas/{mangaId}/rating
admin:       GET /admin/dashboard  POST /admin/jobs/inactivity
             GET /admin/submissions  POST /admin/submissions/{id}/{approve,reject}
             GET /admin/users  GET /admin/users/pending  GET /admin/users/{id}
             POST /admin/users/{id}/{approve,reject}  PATCH /admin/users/{id}/{role,status,quota}
feedback:    POST /feedback
infra:       GET /health  (+ /local-storage/** só em dev)
```

---

## 2. Drift de documentação (`docs/buruna_architecture.md`)

A doc é boa e detalhada, mas **diverge da realidade** em pontos concretos. Não confiar nela como verdade:

| # | O que a doc diz | Realidade no código | Severidade |
|---|---|---|---|
| D1 | Módulos: `auth, user, manga, reader, admin, notification` | Existem ainda **`engagement`** (ratings + reading-list) e **`feedback`** como módulos próprios; **`notification` não é módulo** — vive em `infra/notification` | Média |
| D2 | "Tabelas gerenciadas por Flyway (V1..V18)" + §3.3 lista até V18 | Existem **V19** (formato `LIVRO`) e **V20** (`add_manga_submission`) | Alta |
| D3 | §2.6 (inatividade): e-mails via "**Gmail SMTP**" | Migrado para **Resend** (ADR-29, no mesmo doc) — **contradição interna** | Média |
| D4 | Fluxos §2 não mencionam o **workflow de submissão** (`/my/mangas/{id}/submit` → `/admin/submissions/{id}/approve\|reject`) | É a feature mais nova (V20, commits recentes) e **não está documentada** | Alta |
| D5 | Contexto do projeto: **React 18** | `package.json`: **React 19**, react-router-dom 7, zustand, radix-ui | Baixa |
| D6 | §3 modelo de dados não cita formato `LIVRO` nem colunas de submissão (`submission_status`, `rejection_reason`, `submitted_at`, `reviewed_by`, `reviewed_at`) | Presentes em `Manga` | Média |
| D7 | Há **dois caminhos** para publicar um privado: `promote` (direto, COLLABORATOR+) e `submit`→`approve` (qualquer ACTIVE pede, ADMIN aprova). A doc só descreve `promote` | Ambos coexistem no código | Média (ver risco R6) |

> **Recomendação:** após a refatoração, `docs/buruna_architecture.md` será substituído por `docs/arquitetura.md` (Fase 4). Até lá, tratar o código como verdade.

---

## 3. Pontos de dor / dívida técnica

### 3.1 Domínio anêmico + vazamento de framework no "domínio"
- As entidades em `*/domain` são **bags de `@Getter/@Setter` JPA** (`jakarta.persistence`). Toda regra de negócio vive nos services manipulando setters (ex.: `manga.setPublic(true); manga.setSubmissionStatus(null); ...` espalhado em `PrivateMangaService`). Não há invariantes no domínio.
- O pacote chama-se `domain`, mas é, na prática, o **modelo de persistência** — não um domínio independente de framework. **Hoje é impossível testar regra de negócio sem subir JPA/Spring**, que é exatamente o que a migração quer destravar.

### 3.2 Concern de HTTP vazando para a camada de negócio
- `DomainException(HttpStatus, msg)` carrega **status HTTP** dentro do service. **6 services** referenciam `HttpStatus` (PrivateMangaService: 11 ocorrências). O domínio "sabe" que existe HTTP — acoplamento da regra de negócio à camada web.

### 3.3 God service / violação de SRP
- **`PrivateMangaService` (350 linhas)** acumula: CRUD de mangá privado + geração de upload URL + finalize + cota + **workflow de submissão (submit/approve/reject/listPending)** + **promote**. Os métodos `approveSubmission`/`rejectSubmission`/`listPendingSubmissions` são **admin-facing** mas moram num service chamado "PrivateManga", e são consumidos por `AdminSubmissionController`. Mistura de atores e responsabilidades.

### 3.4 Autorização espalhada em 3 mecanismos (inconsistência)
Não há uma estratégia única. Coexistem:
1. **`@PreAuthorize`** em 7 controllers (`hasAnyRole('COLLABORATOR','ADMIN')` etc.).
2. **Regras de URL** no `SecurityConfig` (`requestMatchers(...).authenticated()/permitAll()`).
3. **Checagem manual de role dentro dos services** (`VolumeService.assertCanModify`, `MangaService` L184, `PrivateMangaService.promote` L261).

Exemplos de fricção: `POST /mangas` é só `.authenticated()` no SecurityConfig mas `@PreAuthorize` no controller restringe a COLLABORATOR+ (defense-in-depth, ok); já `/admin/jobs/**` é `permitAll()` e a proteção real é o header `X-Job-Secret` validado no controller. Difícil raciocinar sobre "quem pode o quê" olhando um lugar só.

### 3.5 Acoplamento entre módulos
- `manga` importa `user.domain` (`User`, `Role`, `UserStatus`) e `user.repository.UserRepository` (em `PrivateMangaService`, para notificar admins).
- **`user` importa `manga`**: `InactivityJob` (pacote `user`) depende de `manga.repository.{Manga,Volume}Repository`, `manga.domain.Manga`, `StorageClient` e `EmailService` — o módulo de usuário **alcança o interior do módulo de mangá** para apagar coleção privada. Dependência bidirecional `user ↔ manga`.

### 3.6 Duplicação de mapeamento
- A montagem de `VolumeResponse`/`toResponse` está **duplicada** em `PrivateMangaService` e `VolumeService` (e parcialmente em `MangaResponseMapper`). Sem um mapper único por agregado.

### 3.7 Dois bugs latentes encontrados no `InactivityJob` (não são escopo da Fase 1, mas registrados)
- **B1 — `@Transactional` ineficaz:** `deactivateUser(...)` é `@Transactional`, mas é chamado por **invocação interna** (`this.deactivateUser`) a partir de `runJob()`. O proxy AOP do Spring é **bypassado** → não há fronteira transacional real por usuário. Deleção de mangás + update de status não estão atomicamente isolados como o código sugere.
- **B2 — paginação sobre conjunto mutável:** `runJob()` re-consulta `findByStatus(ACTIVE, page)` a cada iteração **enquanto muda usuários ACTIVE→INACTIVE** dentro do loop. Como o offset avança sobre um conjunto que encolhe, **usuários podem ser pulados** numa mesma execução. (O job diário acaba pegando os pulados no dia seguinte, o que mascara o bug.)

> Esses dois pontos reforçam o valor da rede de segurança de testes **antes** de tocar o job (ver Fase 3).

### 3.8 Estado atual dos testes — 7 scripts bash, zero teste automatizado
- `src/test` está **vazio** (apesar de `spring-boot-starter-test` e `spring-security-test` já estarem no `pom.xml`).
- A "suíte" são **7 scripts bash (~2.628 linhas)** que batem em um backend **rodando em `localhost`**, validam respostas com `curl`/`jq`/`python3` e checam estado direto no **container Postgres via `psql`**.

| Script | Cobre (aprox.) |
|---|---|
| `test-phase2.sh` | auth + gestão de usuários (registro, login, aprovação, roles/status) |
| `test-phase3.sh` | tags / categorias (admin) |
| `test-phase4.sh` | mangás públicos + volumes (upload em 2 fases, finalize) |
| `test-phase5.sh` | coleção privada (criar mangá, upload, cota) |
| `test-phase6.sh` | reader (URL assinada, progresso, histórico) |
| `test-phase7.sh` | engagement (reading-list + ratings) |
| `test-phase8.sh` | admin dashboard + InactivityJob (verificação via SQL) |

**Problemas:** dependem de ambiente vivo (backend + docker postgres + credenciais reais de admin + `jq`/`python3`), não são determinísticos, não rodam no CI, não isolam regra de negócio. São, na prática, **smoke/e2e manuais**. Servem como *checklist de comportamento* (útil para derivar casos de teste), mas **não dão confiança nem cobertura de regressão**.

> **Importante para não perder cobertura:** cada cenário desses scripts deve virar um teste automatizado equivalente **antes** de remover o script (mapeamento 1:1 na Fase 3).

---

## 4. Candidatos a Bounded Context

Os módulos atuais já têm fronteiras razoáveis. Proposta de contextos (a fechar na Fase 2):

| Bounded Context | Núcleo | Pacotes atuais | Coesão |
|---|---|---|---|
| **Identidade & Acesso** | Usuário, credenciais, JWT/refresh, 2FA, reset de senha, roles, aprovação | `auth`, `user` | Alta |
| **Catálogo (biblioteca pública)** | Mangá público, volumes, tags/categorias, busca/filtro | `manga` (parte pública), `tag` | Alta |
| **Coleção Privada** | Mangá privado do usuário, upload, cota, submissão→publicação | `manga` (parte privada) | Alta (merece extração da god-service) |
| **Leitura & Progresso** | URL assinada de leitura, progresso, histórico, view count | `reader` | Alta |
| **Engajamento** | Lista de leitura, avaliações (avg_rating/rating_count) | `engagement` | Média (CRUD simples) |
| **Administração** | Dashboard, revisão de submissões, gatilho de jobs | `admin` | Baixa (orquestra outros contextos — é mais "casca" do que contexto) |
| **Notificação** (supporting) | E-mail transacional | `infra/notification` | Supporting subdomain |
| **Storage** (supporting) | GCS / local, URLs assinadas | `infra/storage` | Supporting subdomain |

**Observações:**
- **Catálogo** e **Coleção Privada** compartilham a mesma tabela/entidade `Manga` (flag `is_public`). Decidir na Fase 2 se viram **um** agregado `Manga` com dois "modos" ou dois contextos compartilhando modelo — provavelmente **um agregado, dois casos de uso**, para não duplicar.
- **Administração** não é um domínio com regra própria; é orquestração. Sinaliza que **não merece** modelagem DDD pesada (proporcionalidade).

---

## 5. Riscos da migração (o que é perigoso tocar)

Fluxos de produção, ordenados por risco:

| # | Fluxo crítico | Por que é arriscado | Mitigação antes de tocar |
|---|---|---|---|
| R1 | **Upload em 2 fases (Signed URL PUT → finalize)** — público e privado | Efeito colateral no GCS **fora de transação**; falha no finalize deixa arquivo órfão; duplicação de lógica em 2 services | Teste de integração cobrindo upload-url + finalize (mock do `StorageClient`) **antes** de unificar |
| R2 | **Leitura com URL assinada** (`/reader/{id}/url`) | Expiração de 30 min; incrementa `view_count` e grava histórico no mesmo fluxo; quebra a leitura se a assinatura mudar | `@WebMvcTest`/integração garantindo formato da URL e efeitos colaterais |
| R3 | **Cadastro e aprovação** | Envio de e-mail `@Async`, rate limit, captcha, transição de status `PENDING→ACTIVE` | Teste de service com `EmailService` mockado |
| R4 | **Job de inatividade** | Deleta arquivos GCS + mangás do banco; **já tem 2 bugs latentes (B1/B2)**; idempotência frágil | **Rede de segurança obrigatória antes** de refatorar; corrigir B1/B2 como parte da migração, não antes |
| R5 | **Refresh token rotation** | Token rotacionado a cada chamada; corrida pode forçar logout; o front depende do comportamento exato | Teste de `AuthService`/`TokenService` cobrindo rotação e expiração |
| R6 | **Publicação de privado** (`promote` **e** `submit`→`approve`) | Dois caminhos coexistem; validação de título/hash único só contra públicos; cruza Coleção Privada × Catálogo × Admin | Testar ambos os caminhos antes de mover regra de validação |
| R7 | **2FA / reset de senha** | tempToken com claim `purpose:2fa`, invalidação de refresh tokens no reset | Teste de `AuthService`/`TotpService` |

**Princípio para todos:** introduzir o teste automatizado equivalente ao bash **antes** da refatoração do fluxo, e só então remover o script. Nenhum big-bang.

---

## 6. Avaliação de proporcionalidade (conselho honesto)

O pedido é DDD + Clean Arch + SOLID. Para um **projeto solo, sem deadline**, parte disso agrega muito e parte é over-engineering. Minha leitura honesta:

**Vale a pena (alto retorno):**
- **Separar domínio de framework** o suficiente para **testar regra de negócio sem subir Spring** — esse é o objetivo central e justifica o esforço. Entidades de domínio ricas (com invariantes) + serviços de aplicação (use cases) finos.
- **Tirar `HttpStatus` do domínio** (substituir `DomainException(HttpStatus,...)` por exceções de domínio puras + tradução para HTTP só no `@RestControllerAdvice`).
- **Quebrar a god-service** `PrivateMangaService` por caso de uso/ator.
- **Unificar autorização** numa estratégia só (preferir `@PreAuthorize` + checagem de ownership no domínio, eliminar checagem de role solta no service).
- **Resolver o acoplamento `user ↔ manga`** (o `InactivityJob` deveria orquestrar via um caso de uso/porta, não importar repositórios internos de `manga`).

**Cuidado — risco de over-engineering num projeto solo (recomendo a versão leve):**
- **Não** criar interface de repositório custom para cada agregado só por dogma quando Spring Data JPA já entrega a abstração. Criar porta própria **só** onde houver ganho real de teste/troca.
- **Não** adotar CQRS, event sourcing, ou message bus interno — o ADR-03 já decidiu (com razão) que o volume não justifica.
- **Value Objects:** introduzir **poucos e onde pagam** (ex.: `Email`, `Slug`, `Quota`/bytes, `VolumeNumber`). Não transformar toda string em VO.
- **Mapper:** um por agregado, não uma engine genérica.
- **Frontend:** no máximo uma fina camada `api/` tipada; **não** aplicar Clean Arch completa no front — o custo não se paga.
- **"Administração" como bounded context pleno:** não. É orquestração; tratar como casca fina sobre os outros contextos.

> **Em resumo:** o eixo "domínio testável, isolado de framework" merece o investimento completo. As abstrações estruturais (ports/adapters, VOs, mappers) devem ser introduzidas **sob demanda e justificadas**, não por simetria. Onde a Fase 2 propuser uma camada que não se paga, vou marcar explicitamente como "simplificar".

---

## 7. Resumo executivo (TL;DR)

- **Estado real:** monolito modular bem organizado em pacotes por feature, controllers finos, DTOs como records, erro centralizado, e **duas abstrações já corretas** (`StorageClient`, `EmailSender`). Base melhor do que a doc sugere.
- **Drift de doc:** real e concreto (migrations V19/V20 ausentes, workflow de submissão não documentado, Gmail×Resend, módulos errados, React 18×19). Código = verdade.
- **Dívida principal:** domínio anêmico + framework (`HttpStatus`/JPA) dentro do "domínio" → **regra de negócio não é testável sem Spring**. God-service `PrivateMangaService`. Autorização em 3 lugares. Acoplamento `user↔manga`. **Zero teste automatizado** (só 7 bash e2e).
- **Bugs achados de brinde:** `@Transactional` ineficaz por self-invocation (B1) e paginação sobre conjunto mutável (B2) no `InactivityJob`.
- **Maior risco:** os 4 fluxos de produção (upload 2-fases, leitura assinada, inatividade, publicação). Estratégia: **teste-rede-de-segurança antes de refatorar**, 1:1 com os cenários dos bash scripts.
- **Conselho de proporcionalidade:** investir pesado em "domínio testável isolado de framework"; aplicar ports/VOs/mappers **sob demanda**, sem dogma — over-engineering é risco real num projeto solo.

---

## 8. Adendo — Barreira de entrada para contribuidores

> Acrescentado após a Fase 1, ao saber que **o projeto recebe contribuições da comunidade** e que legibilidade/baixa barreira de entrada são objetivo de primeira classe. Avalia o estado atual do onboarding de um dev externo.

### 8.1 O que já existe e ajuda
- **Secrets estão corretos:** `.env` e `gcs-credentials.json` estão no `.gitignore` e **não versionados** (verificado). Não há credencial vazada no repo — pré-condição básica para abrir a contribuição. (`gcs-cors.json` é versionado, mas é só config de CORS, não sensível.)
- **README.md raiz é razoável:** tem stack, diagrama de produção, setup com Docker Compose, tabela de env vars, estrutura de pastas, comandos úteis e deploy. Para um projeto solo, está acima da média.
- **`docs/buruna_architecture.md`** dá contexto técnico profundo (fluxos, ADRs) — ótimo material de fundo para quem chega.
- **Padrão de módulo previsível:** a estrutura `controller/service/domain/repository/dto/exception` repetida em todos os módulos é fácil de pegar. Bom para contribuidor — **vale preservar essa previsibilidade na arquitetura-alvo**.

### 8.2 Onde um recém-chegado se perde (gaps reais)
| # | Gap | Impacto no contribuidor |
|---|---|---|
| C1 | **Não existe `CONTRIBUTING.md`** | Sem fluxo de PR, padrão de branch/commit, como rodar testes, checklist, código de conduta. Contribuidor não sabe "como contribuir corretamente". |
| C2 | **Sem templates em `.github/`** (só `workflows/deploy.yml`) | Sem template de issue nem de PR. Issues/PRs chegam sem padrão, sem critério de aceite. |
| C3 | **README pede mais do que o necessário para rodar local** | Lista como pré-requisito: bucket GCS, Service Account `storage.objectAdmin` e Gmail App Password. **Mas existe `LocalStorageClient` + profile `application-local.yml`** que permite rodar **sem GCS e sem e-mail**. O caminho de menor atrito **não está documentado** → barreira artificial enorme: o dev acha que precisa de conta GCP só para subir o projeto. **Maior gargalo de onboarding hoje.** |
| C4 | **README desatualizado (drift) confunde quem chega** | Diz Flyway "V1–V16" (real V20), "React 18" (real 19), "Gmail SMTP via Spring Mail" (real Resend HTTP). A estrutura lista um módulo `notification/` que **não existe** (é `infra/notification`). Um contribuidor que confia no README parte de premissas falsas. |
| C5 | **"rodar testes do backend: `./mvnw test`" é enganoso** | Não há **nenhum** teste Java; o comando não exercita nada. O contribuidor não tem como validar sua mudança antes do PR (nem sabe que a "suíte" real são 7 bash que exigem ambiente vivo). |
| C6 | **`frontend/README.md` é o boilerplate padrão do Vite** | Texto genérico "React + TypeScript + Vite", sem nada específico do Burūna. Inútil para onboarding do front. |
| C7 | **Sem documento de convenções** | Padrões de nomenclatura, camadas, tratamento de erro, teste — hoje só inferíveis lendo o código. Aumenta o tempo até o primeiro PR útil. |

### 8.3 Implicação para as Fases seguintes
- **Fase 2:** "facilidade para um contribuidor novo entender e navegar" entra como **critério explícito** de proporcionalidade — preferir padrões previsíveis/convencionais a soluções espertas; quando uma escolha de DDD for mais correta porém menos óbvia para quem chega de fora, **apontar o trade-off e preferir a versão mais aproachável**. Preservar a regularidade `controller/service(use case)/domain/repository` que o contribuidor já reconhece.
- **Fase 4:** gerar `CONTRIBUTING.md` + `README.md` reescrito (incl. **caminho local-only sem GCP** como padrão de setup), `docs/convencoes.md`, e templates de issue/PR em `.github/`. Corrigir C3–C6 é parte do entregável.

---

**➡️ Próximo passo:** aguardando sua validação para iniciar a **Fase 2 — Arquitetura-alvo** (camadas Clean Arch, modelagem DDD definitiva, SOLID aplicado, estrutura de pastas, ADRs e avaliação de proporcionalidade por contexto — agora com **legibilidade/baixa barreira para contribuidores** como critério de primeira classe).
