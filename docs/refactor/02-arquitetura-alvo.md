# Fase 2 — Arquitetura-alvo (Burūna)

> 📋 **Documento de processo — diário da migração, não documentação viva.** Registra o
> design da arquitetura-alvo decidido durante a refatoração. Para o estado atual e já
> implementado, ver [docs/ARCHITECTURE.md](../ARCHITECTURE.md) e [CLAUDE.md](../../CLAUDE.md).

> **Base:** diagnóstico aprovado em `01-analise-estado-atual.md`.
> **Decisões já travadas (Fase 1):** (a) `Manga` = **um agregado, dois casos de uso** (público × privado); (b) bugs B1/B2 do `InactivityJob` corrigidos **durante** a refatoração do contexto, com teste antes; (c) **DDD pragmático** — domínio testável + abstrações sob demanda; (d) **contribuições da comunidade são objetivo de primeira classe** — legibilidade e baixa barreira de entrada pesam como critério explícito.
> **Natureza:** documento de design. Nenhum código foi alterado. As ADRs novas estão em `docs/adr/` (ADR-31…ADR-38), no mesmo estilo das ADRs existentes.

---

## 1. Princípios da arquitetura-alvo

1. **Domínio testável sem framework é o eixo.** O objetivo central é poder testar regra de negócio em JUnit puro, sem subir Spring nem banco.
2. **Pragmatismo > pureza.** Cada camada/abstração só existe se pagar o próprio custo. Onde a pureza de Clean Arch/DDD cobra ceremônia sem ganho, escolhemos a versão mais leve — e documentamos o porquê.
3. **Legibilidade e previsibilidade para o contribuidor.** Padrões convencionais e regulares > soluções espertas. Um dev externo deve conseguir abrir um contexto e reconhecer imediatamente onde fica o quê. Quando a opção "mais DDD" for menos óbvia para quem chega de fora, apontamos o trade-off e ficamos com a mais aproachável.
4. **Produção é sagrada.** A arquitetura-alvo é o destino; o caminho (Fase 3) é incremental, sem big-bang.
5. **Preservar o que já está bom.** Controllers finos, DTOs como `record`, erro centralizado, `StorageClient`/`EmailSender` e a regularidade `controller/service/domain/repository` permanecem — são justamente o que o contribuidor já reconhece.

---

## 2. Camadas Clean Arch e regra de dependência

### 2.1 As quatro camadas (por bounded context)

| Camada | Pacote | Contém | Depende de | Framework? |
|---|---|---|---|---|
| **Domain** | `domain/` | Entidades ricas (com invariantes/comportamento), Value Objects, enums, exceções de domínio, domain services puros | **Nada** (nem Spring, nem `HttpStatus`, nem outro contexto) | Só JPA como *metadado passivo* (ver §3) |
| **Application** | `application/` | Casos de uso (orquestração), comandos/resultados, fronteira transacional, interfaces de porta que o caso de uso precisa | `domain` + portas (`StorageClient`, `EmailSender`, repositórios) | Spring (`@Service`, `@Transactional`) |
| **Persistence** | `persistence/` | Repositórios Spring Data JPA, `Specification`, projeções | `domain` | Spring Data |
| **Web** | `web/` | Controllers, DTOs de request/response, `@PreAuthorize` | `application` | Spring MVC |

### 2.2 Regra de dependência (direção das setas)

```
        web  ─────────►  application  ─────────►  domain
                              │                      ▲
                              ▼                      │
                         persistence  ──────────────┘
                              │
                              ▼
                    (portas: StorageClient, EmailSender — em shared/)
```

- **As setas só apontam para dentro.** `domain` não conhece ninguém. `web` nunca fala direto com `persistence` nem com entidades sem passar pela `application`.
- **`persistence` depende de `domain`** (devolve entidades/agregados), e os repositórios são consumidos pela `application`.
- **Sem dependência entre internals de contextos diferentes.** Um contexto só toca outro através da **camada `application`** do outro (um caso de uso público), nunca importando seu `domain`/`persistence`. Isso elimina o acoplamento `user ↔ manga` de hoje (ADR-35 / §5).

### 2.3 Tabela "quem pode importar o quê"

| De ↓ \ Para → | domain | application | persistence | web | shared (storage/email/security) |
|---|:---:|:---:|:---:|:---:|:---:|
| **domain** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **application** | ✅ | ✅ (mesmo contexto) | ✅ (interface) | ❌ | ✅ (portas) |
| **persistence** | ✅ | ❌ | ✅ | ❌ | ❌ |
| **web** | ✅ (só p/ tipos de leitura) | ✅ | ❌ | ✅ | ❌ |
| **outro contexto** | ❌ | ✅ (use case público) | ❌ | ❌ | — |

> **Como o contribuidor lê isto:** "domínio não importa nada; controller chama use case; use case usa repositório e portas; um módulo fala com outro só pelo use case do outro." Regra simples de memorizar e de revisar em PR.

---

## 3. Decisão-chave: domínio rico **anotado com JPA** (não separar entidade de domínio da entidade JPA)

Esta é a decisão mais importante e a que mais afeta legibilidade. Detalhada na **ADR-32**.

- **Opção canônica (pura):** uma entidade de domínio livre de framework + uma entidade JPA separada + mapper entre as duas, por agregado.
- **Opção escolhida (pragmática):** **uma** classe de domínio rica (comportamento + invariantes), ainda anotada com JPA.

**Por que a pragmática, mesmo "menos pura":**
- O que **impede** testar regra hoje **não** são as anotações JPA — é (a) `HttpStatus`/lógica nos services e (b) entidades anêmicas. Anotações JPA são **metadado passivo**: `new Manga(...).promote()` não toca o banco. Dá para testar comportamento em JUnit puro mesmo com `@Entity` na classe.
- A opção pura **dobra o número de classes e adiciona um mapper por agregado** — exatamente a ceremônia que mais confunde um contribuidor novo ("por que existem dois `Manga`?") e que não se paga num projeto deste tamanho.
- **Trade-off honesto (registrado na ADR):** o domínio não é 100% framework-free e um repositório JPA pode persistir um estado que passou pela invariante por um setter legado. Mitigação: **encapsular** (setters privados/`protected`, mutação só por métodos de negócio, construtor/factory que valida). Se algum dia um agregado precisar de pureza total (improvável aqui), separa-se só aquele.

> Resultado: ricas o suficiente para testar e proteger invariantes; simples o suficiente para um contribuidor entender em minutos.

---

## 4. Modelagem DDD

### 4.1 Bounded contexts definitivos e mapa de contexto

| Bounded Context | Pacote raiz | Papel (DDD) | Relação |
|---|---|---|---|
| **Identity & Access** | `identity` (funde `auth`+`user`) | Core | Fornece `userId`/papéis aos demais (upstream) |
| **Catalog & Collection** | `manga` | **Core domain** (coração do produto) | Consome identity (downstream); publica use cases p/ admin e reading |
| **Reading** | `reading` | Supporting | Consome catalog (URL/volume) e identity |
| **Engagement** | `engagement` | Supporting | Consome catalog (atualiza `avgRating`) e identity |
| **Administration** | `admin` | **Casca / orquestração** (sem domínio próprio) | Orquestra use cases dos outros contextos |
| **Notification** | `shared/notification` | Generic subdomain | Porta `EmailSender` |
| **Storage** | `shared/storage` | Generic subdomain | Porta `StorageClient` |

**Decisões de fronteira:**
- **Catalog e Collection no mesmo contexto** (`manga`), com **um agregado `Manga`** distinguido por `isPublic` e **casos de uso separados** (catálogo público × coleção privada × submissão/promoção). Decisão travada na Fase 1. Evita duplicar entidade/tabela. (ADR-34)
- **`auth` + `user` fundidos em `identity`:** hoje estão acoplados (login lê `User.status`, aprovação muda status, 2FA mora em `auth` mas é atributo de `User`). São o mesmo subdomínio. Unir reduz o vai-e-vem e dá um lar claro para `User` como raiz.
- **`Administration` não é contexto com modelo** — é orquestração. **Não recebe** tratamento DDD (ver proporcionalidade §8). Um `AdminSubmissionController` chama o use case `ReviewSubmissionUseCase` do contexto `manga`.

### 4.2 Agregados, entidades, value objects e raízes

| Agregado (raiz) | Entidades internas | Value Objects | Invariantes no domínio |
|---|---|---|---|
| **`Manga`** (root) | `Volume` (composição: cascade/orphanRemoval) | `Slug`, `VolumeNumber`, `FileHash`, `Synopsis?` | nº de volume único no mangá; só promove se privado e dono é COLLABORATOR+; não submete se já público/pendente; transições de `submissionStatus` válidas |
| **`User`** (root) | — | `Email`, `Username`, `Quota` (limitBytes/usedBytes) | transições de status válidas (`PENDING→ACTIVE→INACTIVE`); `canFit(bytes)` na cota |
| **`Tag`** / **`TagCategory`** | — (referência) | — | reference data administrada; `Manga` referencia `Tag` por id |
| **`Rating`** (root) | — | `Score` (1–5) | um por (user, manga); score no range |
| **`ReadingList`** (root) | — | — | um por (user, manga) |
| **`ReadingProgress`** (root) | — | `PageNumber` | um por (user, volume); upsert |
| **`RefreshToken`** / **`PasswordResetToken`** | — | — | expiração; rotação |

**Notas de fronteira de agregado:**
- **`Volume` só é modificado através de `Manga`** (`manga.addVolume(...)`, `manga.removeVolume(...)`), o que move a checagem de "número duplicado" e cota para dentro do agregado/uso de caso, em vez de espalhada (ADR-34).
- **`Rating` → `Manga.avgRating`/`ratingCount` é cross-aggregate.** A regra pura DDD pediria consistência eventual; aqui mantemos **recálculo síncrono na mesma transação** (como hoje), porque o volume é baixo e a complexidade de eventos não se paga. Trade-off registrado na ADR-34.
- **Value Objects são seletivos** (ADR-34): só `Slug`, `VolumeNumber`, `FileHash`, `Quota`, `Email`, `Username`, `Score` — onde encapsulam regra/validação real. **Não** transformamos toda string em VO (over-engineering e ruído para o contribuidor).

### 4.3 Domain services — só os que se justificam

Domain service = lógica de domínio que não pertence naturalmente a **uma** entidade. Mantemos **poucos**, e separamos a parte **pura** (domínio) da parte que precisa de I/O (vai para o use case):

| Candidato | Veredito | Onde fica |
|---|---|---|
| **`InactivityPolicy`** | ✅ Domain service **puro** | `domain`: `decide(lastAccessAt, now) → NONE \| WARN \| DEACTIVATE`. Testável sem Spring. Corrige B1/B2 ao separar política de orquestração (ADR-36/§ Job). |
| **`QuotaPolicy`** (via VO `Quota`) | ✅ no VO | `Quota.canFit(bytes)` / `remaining()` puro. A busca de bytes usados é do use case. |
| **`PromotionPolicy`** | ⚠️ Parcial | A **regra** ("o que é conflito de título/hash") fica no domínio; a **consulta** (existe público com esse título/hash?) fica no use case com o repositório. Não criar um service que injeta repositório só por simetria. |
| **`SlugGenerator`** | ⚠️ Dividido | Normalização (`Slug.from(title)`) é VO puro; resolução de unicidade (sufixo) é use case + repositório. |
| `DuplicateCheckService` genérico | ❌ | Não criar abstração genérica; cada use case chama o repositório direto. |

### 4.4 Linguagem ubíqua (glossário resumido)

Glossário completo será `docs/glossario-dominio.md` (Fase 4). Núcleo:

| Termo | Significado no domínio |
|---|---|
| **Manga** | Obra. Pode estar **pública** (catálogo) ou **privada** (coleção do dono). Raiz de agregado, contém Volumes. |
| **Volume** | Arquivo PDF de um número dentro de um Manga. Identidade por (manga, volumeNumber). |
| **Owner** | Usuário dono de um Manga privado. |
| **Promote** | COLLABORATOR+ move o **próprio** privado direto para o catálogo público. |
| **Submission** | Qualquer usuário ACTIVE **pede** publicação; ADMIN **aprova/rejeita** (`PENDING/REJECTED`). |
| **Quota** | Limite de bytes de coleção privada por usuário. |
| **Signed URL** | URL temporária do GCS para leitura/upload sem passar pelo backend. |
| **Inactivity** | Política: avisa em 75 dias sem acesso, desativa e apaga coleção privada em 90. |
| **Reading Progress** | Página atual de um usuário num Volume. |
| **Rating** | Nota 1–5 de um usuário num Manga público; alimenta `avgRating`. |

---

## 5. SOLID aplicado — onde criar interface e onde **não**

| Decisão | Criar abstração? | Razão |
|---|---|---|
| `StorageClient` (GCS/local) | ✅ **Sim** (já existe) | DIP legítimo: troca de provedor + rodar local. Mantém. |
| `EmailSender` (Resend/no-op) | ✅ **Sim** (já existe) | Idem (ADR-29). |
| **`Clock` injetável** | ✅ **Sim** (novo, ADR-36) | `OffsetDateTime.now()` espalhado impede testar inatividade/expiração. `java.time.Clock` é convencional e barato. |
| Repositórios | ❌ **Não** criar porta custom (ADR-37) | Spring Data **já é** a abstração. Envelopar em interface própria é ceremônia sem ganho e confunde o contribuidor. Decisão de **não-abstração** explícita. |
| Um `Service`/interface por classe | ❌ **Não** | Interface sem segundo implementador é ruído. |
| Mapper genérico/engine | ❌ **Não** | Um mapper simples por agregado, código óbvio. |

- **SRP:** quebrar `PrivateMangaService` (350 l) em casos de uso por intenção (`CreatePrivateMangaUseCase`, `UploadVolumeUseCase`, `SubmitForApprovalUseCase`, `ReviewSubmissionUseCase`, `PromoteMangaUseCase`…). Contextos CRUD simples (engagement, tags) podem manter **um** application service com poucos métodos — proporcionalidade, não um arquivo por método.
- **OCP/LSP/ISP:** portas pequenas e focadas (`StorageClient` já é coeso).

---

## 6. Estrutura de pastas alvo

### 6.1 Backend

```
com.buruna/
├── identity/                  (auth + user fundidos)
│   ├── domain/                User (root, rico), Role, UserStatus, VOs (Email, Username, Quota),
│   │                          RefreshToken, PasswordResetToken, InactivityPolicy, exceções
│   ├── application/           subpastas por área (evita pasta gigante — legibilidade):
│   │   ├── authentication/    Login, Refresh, Logout, Enable2fa, ResetPassword, ForgotPassword
│   │   ├── account/           Register, DeleteAccount, quota, 2FA status
│   │   └── admin/             ApproveUser, RejectUser, ChangeRole/Status/Quota, RunInactivity
│   ├── persistence/           UserRepository, RefreshTokenRepository, PasswordResetTokenRepository
│   └── web/                   AuthController, AdminUserController, DTOs request/response
│
├── manga/                     (Catalog & Collection — CORE)
│   ├── domain/                Manga (root), Volume, Tag, TagCategory, enums,
│   │                          VOs (Slug, VolumeNumber, FileHash), exceções de domínio
│   ├── application/           CatalogQuery, PrivateCollection use cases, Volume use cases,
│   │                          Submission/Promote use cases, Tag use cases, QuotaService
│   ├── persistence/           MangaRepository, VolumeRepository, TagRepository, MangaSpecification
│   └── web/                   MangaController, PrivateMangaController, VolumeController, TagController
│
├── reading/                   ReadingProgress, ReadingHistory + use cases + ReaderController
├── engagement/                Rating, ReadingList + use cases + controllers
├── admin/                     (casca) DashboardController, JobController, AdminSubmissionController
│                              → só orquestram use cases dos contextos acima
│
└── shared/                    (antigo infra/)
    ├── storage/               StorageClient + Gcs/Local adapters
    ├── notification/          EmailSender + Resend/no-op
    ├── security/              filtros JWT/rate-limit, SecurityConfig, @PreAuthorize support
    ├── web/                   GlobalExceptionHandler, ErrorResponse, tradução exceção→HTTP
    ├── time/                  Clock config
    └── config/                AppProperties, OpenApi, etc.
```

> **Nota de contribuidor:** o esqueleto `domain/application/persistence/web` se repete em **todo** contexto — mesma previsibilidade do `controller/service/domain/repository` atual, só que com as fronteiras de Clean Arch explícitas. `shared/` substitui `infra/` com submódulos óbvios.

### 6.2 Frontend (arquitetura leve — ADR-31 / proporcionalidade)

```
frontend/src/
├── api/            ← NOVO: uma fina camada tipada por contexto (mangaApi.ts, authApi.ts…),
│                     encapsula axios. Telas param de chamar axios cru.
├── types/          ← NOVO: tipos de request/response compartilhados (espelham DTOs do backend)
├── pages/          (inalterado na estrutura)
├── components/     (inalterado)
├── store/          (zustand — auth)
└── lib/            (axios, signedUrlCache, utils)
```

> **Sem Clean Arch no frontend.** O ganho não se paga e adicionaria barreira para contribuidores de front. Só formalizamos a camada `api/` (hoje as telas chamam `axios` direto) e tipos compartilhados. (ADR-31)

---

## 7. Exemplo de um contexto migrado de ponta a ponta — `manga` / **Promote**

Fatia vertical representativa (tem invariante de domínio, autorização por ownership, regra cross-tabela e tradução de erro). **Código ilustrativo** do destino, não para colar.

**7.1 Domain — invariante na raiz do agregado** (`manga/domain/Manga.java`)
```java
// setters de negócio privados/protected; mutação só por métodos com regra.
// NOTA (ADR-35): o agregado NÃO recebe a entidade User de identity — isso violaria
// a regra de dependência (manga.domain não importa identity.domain). Papel (RBAC)
// é coberto por @PreAuthorize na borda; posse (ownership) é garantida pelo use case
// via actorId. O domínio só enforça invariantes do PRÓPRIO contexto manga.
public void promoteToPublic() {
    if (this.isPublic)
        throw new MangaAlreadyPublicException(this.id);
    this.isPublic = true;
    this.submissionStatus = null;
}
```

**7.2 Application — use case orquestra (transação, repositório, regra cross-tabela)** (`manga/application/PromoteMangaUseCase.java`)
```java
@Service
public class PromoteMangaUseCase {
    private final MangaRepository mangaRepo;            // Spring Data (sem porta custom — ADR-37)
    // ctor injection

    // Recebe um PRIMITIVO (actorId: UUID), não a entidade User de identity — mantém
    // manga.application livre de identity.domain (ADR-35). Posse garantida pela query.
    @Transactional
    public PrivateMangaResponse handle(UUID mangaId, UUID actorId) {
        Manga manga = mangaRepo.findOwnedPrivate(mangaId, actorId)   // ownership no use case
                .orElseThrow(() -> new MangaNotFoundException(mangaId));

        if (mangaRepo.existsPublicByTitle(manga.title()))
            throw new PublicTitleConflictException(manga.title());
        if (manga.volumeHashes().stream().anyMatch(mangaRepo::existsPublicByFileHash))
            throw new PublicVolumeConflictException();

        manga.promoteToPublic();                       // invariante de manga, sem User
        manga.resolveSlugConflict(mangaRepo::existsBySlug); // unicidade via callback (sem service extra)
        return MangaMapper.toPrivateResponse(manga);   // mapper simples do agregado
    }
}
```

> Quando a regra for "dono **ou** ADMIN" (ex.: deletar volume), o use case recebe `actorId` **e** o papel como primitivo (`Role`/`boolean isAdmin`) — nunca a entidade `User`. RBAC global continua no `@PreAuthorize`.

**7.3 Web — controller fino + autorização na borda** (`manga/web/PrivateMangaController.java`)
```java
@PreAuthorize("hasAnyRole('COLLABORATOR','ADMIN')")   // RBAC na borda (ADR-35)
@PostMapping("/{id}/promote")
public PrivateMangaResponse promote(@PathVariable UUID id, @AuthenticationPrincipal User user) {
    return promoteMangaUseCase.handle(id, user.getId());   // passa só o primitivo
}
```

**7.4 Exceção de domínio pura + tradução só na borda** (ADR-33)
```java
// manga/domain — sem HttpStatus
public class MangaAlreadyPublicException extends DomainException { ... }

// shared/web/GlobalExceptionHandler — único lugar que mapeia para HTTP
@ExceptionHandler(MangaAlreadyPublicException.class)
ResponseEntity<ErrorResponse> handle(...) { return build(HttpStatus.BAD_REQUEST, ...); }
```

**7.5 Teste — domínio puro, sem Spring** (`manga/domain/MangaTest.java`)
```java
@Test
void shouldThrow_whenPromotingAlreadyPublicManga() {
    Manga manga = MangaFixtures.publicManga();
    assertThatThrownBy(() -> manga.promoteToPublic(collaborator()))
        .isInstanceOf(MangaAlreadyPublicException.class);
}
```
E o use case com mocks (`@ExtendWith(MockitoExtension.class)`), `@DataJpaTest` para `MangaRepository.existsPublicByTitle`, `@WebMvcTest` para o controller. Nenhum desses precisa subir o contexto Spring completo.

> Comparação direta com hoje: a mesma regra está espalhada em `PrivateMangaService.promote` (checagem de role solta + setters + `HttpStatus`). No destino: invariante no agregado, orquestração no use case, autorização na borda, erro traduzido num lugar só, e **tudo testável isolado**.

---

## 8. Avaliação de proporcionalidade por contexto

Critérios: **valor da regra de negócio**, **risco em produção** e **legibilidade para o contribuidor**.

| Contexto | Tratamento | Por quê |
|---|---|---|
| **manga (Catalog & Collection)** | **DDD completo** (agregado rico, VOs, use cases por intenção) | Core domain, mais regra e mais risco (upload, promote, submissão). Onde o investimento se paga. |
| **identity** | **DDD médio-alto** | `User` rico (transições de status, cota, 2FA) + auth com lógica real (rotação, TOTP). VOs `Email`/`Quota` pagam. |
| **reading** | **Simplificado** | Quase CRUD + URL assinada. `ReadingProgress`/`History` entidades simples; 1 application service, poucos VOs. Não inflar. |
| **engagement** | **Simplificado (leve)** | Rating/ReadingList são CRUD; única sutileza é o recálculo de `avgRating` (síncrono). 1 service por entidade, sem agregado pesado. |
| **admin** | **Casca, sem DDD** | Orquestração pura. Controllers chamam use cases dos outros contextos. Modelar domínio aqui seria over-engineering. |
| **shared (storage/notification/security)** | **Ports + adapters** | Abstrações já justificadas. Nada de domínio. |
| **frontend** | **Arquitetura leve** | `api/` + `types/`. Sem Clean Arch — custo não se paga e elevaria a barreira para contribuidores de front. |

> **Onde eu te seguraria se você pedisse "mais":** separar entidade de domínio da JPA em todo agregado; criar ports para repositórios; CQRS/eventos para o `avgRating`; bounded context pleno para `admin`; VO para toda string; Clean Arch no front. Tudo isso aumentaria a barreira de contribuição sem ganho proporcional. Marcado explicitamente como **não fazer** (ADR-32, -34, -37).

---

## 9. ADRs desta fase (`docs/adr/`)

Numeração continua de onde a doc atual parou (ADR-30 → ADR-31+), mesmo estilo (Contexto / Decisão / Por quê / Tradeoff).

| ADR | Decisão |
|---|---|
| **ADR-31** | Camadas Clean Arch pragmáticas por bounded context + regra de dependência (inclui front leve) |
| **ADR-32** | Domínio rico **anotado com JPA** (não separar domain model de entidade JPA) |
| **ADR-33** | Exceções de domínio puras; tradução para HTTP só no `@RestControllerAdvice` |
| **ADR-34** | Agregados e fronteiras: `Manga` root c/ `Volume`; um agregado público/privado; cross-aggregate síncrono (`avgRating`); VOs seletivos |
| **ADR-35** | Autorização unificada: `@PreAuthorize` na borda + ownership como regra de domínio; fim da checagem de role nos services; fim do acoplamento `user↔manga` |
| **ADR-36** | `java.time.Clock` injetável + `InactivityPolicy` no domínio (corrige B1/B2) |
| **ADR-37** | Repositórios permanecem Spring Data (não criar ports custom) — não-abstração deliberada |
| **ADR-38** | Estratégia de testes: pirâmide (domínio puro / `@DataJpaTest` / `@WebMvcTest` / integração nos críticos), AAA, `should…when…`, substituição 1:1 dos bash |

---

**➡️ Próximo passo:** aguardando sua validação da arquitetura-alvo e das ADRs. Aprovado, sigo para a **Fase 3 — Roadmap de refatoração** (sequência incremental de issues acionáveis, ordenadas por risco, cada uma um PR pequeno com Definition of Done e estratégia de teste, começando pelo contexto de menor risco para validar o padrão antes dos fluxos críticos).
