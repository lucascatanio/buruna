# Fase 3 — Roadmap de refatoração (Burūna)

> **Base:** `01-analise-estado-atual.md` (diagnóstico) + `02-arquitetura-alvo.md` + ADR-31…38, ambos aprovados.
> **Natureza:** plano de execução. Nenhum código foi alterado. Cada item abaixo é uma **issue acionável**, formatada para colar direto no GitHub Issues/Projects.
> **Regra de ouro:** produção é sagrada. Cada issue é um PR pequeno; o sistema compila, sobe e passa nos testes **antes e depois** de cada uma.

---

## 1. Princípios do roadmap

1. **Do menor risco para o maior.** Validamos o padrão Clean Arch num contexto seguro (`engagement`) antes de tocar fluxos de produção (upload, identidade, inatividade).
2. **Teste antes de refatorar.** Para cada fluxo, a issue de *rede de segurança* (portar os cenários do bash para teste automatizado) vem **antes** da issue de refatoração. O script bash equivalente só é removido **depois** que o teste automatizado cobre os mesmos cenários (mapeamento 1:1).
3. **Vertical por contexto.** Cada contexto é migrado de ponta a ponta (domain → application → persistence → web + testes), não horizontalmente.
4. **Coexistência durante a transição.** As exceções puras (ADR-33) e o `Clock` (ADR-36) são introduzidos coexistindo com o legado; o `DomainException(HttpStatus)` antigo só é removido na limpeza final, quando ninguém mais o usa.
5. **Schema estável.** A arquitetura-alvo mantém as mesmas tabelas. **Quase nenhuma issue precisa de Flyway** — é refatoração de código, não de banco. Isso é proposital e reduz o risco.
6. **Legibilidade para o contribuidor** é critério de Definition of Done: nomes convencionais, padrão regular entre contextos, sem abstração órfã.

### Convenção de cada issue
```
- Contexto/Objetivo · Escopo (in) · Fora de escopo · Arquivos afetados
- Flyway? · Testes · Definition of Done · Tamanho (S/M/L) · Depende de
```

---

## 2. Sequência geral (ordem por dependência e risco)

```
Epic 0  Fundação (testes + CI + plumbing)          ── sem risco de produção
   │
Epic 1  PILOTO: engagement                          ── menor risco, valida o padrão
   │                                                   ◇ MARCO: revisar o padrão antes de replicar
Epic 2  reading                                     ── médio (R2 leitura assinada)
   │                                                   ◇ MARCO PROD: testar leitura
Epic 3  identity (auth+user)                         ── alto (R3 cadastro, R5 refresh, R7 2FA)
   │                                                   ◇ MARCO PROD: login/registro/2FA/refresh
Epic 4  manga (catálogo + coleção + volumes)         ── MAIOR risco (R1 upload, R6 promote/submissão)
   │                                                   ◇ MARCO PROD: upload público/privado, promote, submissão
Epic 5  inatividade + admin                          ── alto (R4 + bugs B1/B2 + cross-contexto)
   │                                                   ◇ MARCO PROD: rodar job e verificar
Epic 6  limpeza final + frontend leve                ── baixo
```

### Mapa bash → contexto (cobertura a não perder)
| Script atual | Cobre | Migra em | Removido em |
|---|---|---|---|
| `test-phase7.sh` | engagement (reading-list + ratings) | Epic 1 | 1.3 |
| `test-phase6.sh` | reader (URL assinada, progresso, histórico) | Epic 2 | 2.3 |
| `test-phase2.sh` | auth + usuários (registro, login, aprovação, roles) | Epic 3 | 3.5 |
| `test-phase3.sh` | tags / categorias | Epic 4 | 4.9 |
| `test-phase4.sh` | mangás públicos + volumes (upload 2-fases) | Epic 4 | 4.9 |
| `test-phase5.sh` | coleção privada (cota, upload) | Epic 4 | 4.9 |
| `test-phase8.sh` | admin dashboard + inatividade (via SQL) | Epic 5 | 5.5 |

### Marcos de validação manual em produção
Após o deploy de cada um destes, **pare e teste manualmente em produção** antes de seguir:
- **M1** (fim Epic 2): abrir um mangá, ler, virar página, confirmar progresso salvo; deixar a URL assinada expirar e confirmar renovação.
- **M2** (fim Epic 3): registrar conta, aprovar, login com e sem 2FA, refresh automático, reset de senha.
- **M3** (durante/fim Epic 4): upload público (COLLABORATOR), upload privado + cota, `promote`, fluxo de submissão→aprovação.
- **M4** (fim Epic 5): disparar `POST /admin/jobs/inactivity` num usuário de teste com `last_access_at` antigo e conferir aviso/desativação + limpeza GCS.

---

## 3. Epic 0 — Fundação (testes, CI, plumbing) · sem risco de produção

### [0.1] Infra de testes + smoke test de contexto
- **Contexto/Objetivo:** Hoje `src/test` está vazio. Habilitar a pirâmide de testes (ADR-38).
- **Escopo (in):** Adicionar dependência Testcontainers (postgres) ao `pom.xml`; criar estrutura `src/test/java/com/buruna/...`; um teste `@SpringBootTest` que valida o context-load; helpers/fixtures base (ex.: `Clock.fixed`).
- **Fora de escopo:** Testes de regra (vêm por contexto).
- **Arquivos:** `backend/pom.xml`, `src/test/.../SmokeApplicationTest.java`.
- **Flyway?** Não.
- **Testes:** o próprio smoke + um teste de domínio trivial provando que JUnit puro roda sem Spring.
- **DoD:** `./mvnw test` executa e passa localmente; documentado como rodar.
- **Tamanho:** S · **Depende de:** —

### [0.2] CI: rodar testes em cada PR
- **Objetivo:** `mvn test` no GitHub Actions (hoje o workflow só faz deploy).
- **Escopo (in):** Job de CI que roda em PR/push para `dev`; Docker disponível para Testcontainers.
- **Arquivos:** `.github/workflows/ci.yml` (novo).
- **Flyway?** Não.
- **Testes:** o pipeline roda a suíte do 0.1.
- **DoD:** PR mostra status de checks verde/vermelho; falha de teste bloqueia merge.
- **Tamanho:** S · **Depende de:** 0.1

### [0.3] Renomear `infra/` → `shared/` com submódulos
- **Objetivo:** Alinhar à estrutura-alvo (`shared/storage|notification|security|web|time|config`).
- **Escopo (in):** Refactor mecânico de pacote (IDE "Rename/Move"), **sem mudança de comportamento**.
- **Fora de escopo:** Qualquer mudança de lógica.
- **Arquivos:** todo `com.buruna.infra.*` → `com.buruna.shared.*` (e imports).
- **Flyway?** Não.
- **Testes:** os existentes (0.1) continuam verdes.
- **DoD:** compila, app sobe, suíte verde; diff é só package/imports.
- **Tamanho:** M (diff grande, risco baixo) · **Depende de:** 0.1

### [0.4] `Clock` bean + base de exceções de domínio puras (coexistindo)
- **Objetivo:** Plumbing dos ADR-36 e ADR-33 sem migrar ninguém ainda.
- **Escopo (in):** Bean `Clock` (`systemDefaultZone`) em `shared/time`; nova hierarquia `DomainException` **pura** (sem `HttpStatus`) em `shared/exception`; o `GlobalExceptionHandler` passa a tratar **ambas** (a pura e a legada `DomainException(HttpStatus)`).
- **Fora de escopo:** Converter exceções existentes (é por contexto).
- **Arquivos:** `shared/time/ClockConfig.java`, `shared/exception/DomainException.java` (nova base pura + renomear a legada p/ `LegacyHttpDomainException` ou manter as duas), `shared/web/GlobalExceptionHandler.java`.
- **Flyway?** Não.
- **Testes:** `@WebMvcTest` mínimo provando que uma exceção pura vira o `ErrorResponse` correto.
- **DoD:** ambos os tipos de exceção resolvem para HTTP corretamente; nada de comportamento muda para o cliente.
- **Tamanho:** M · **Depende de:** 0.1

---

## 4. Epic 1 — PILOTO: contexto `engagement` · menor risco

> Objetivo do epic: provar o padrão Clean Arch ponta a ponta no contexto mais simples. **Marco de revisão** ao fim antes de replicar.

### [1.1] Rede de segurança: portar `test-phase7.sh`
- **Objetivo:** Cobrir ratings + reading-list com teste automatizado antes de refatorar.
- **Escopo (in):** `@WebMvcTest`/`@DataJpaTest`/integração cobrindo: criar/atualizar/remover rating, recálculo de `avgRating`, unicidade (user,manga), reading-list CRUD e unicidade.
- **Arquivos:** `src/test/.../engagement/*`.
- **Flyway?** Não.
- **DoD:** testes verdes reproduzem os cenários do `test-phase7.sh`; **script ainda não removido**.
- **Tamanho:** M · **Depende de:** 0.1

### [1.2] Migrar `engagement` para Clean Arch
- **Objetivo:** Aplicar o padrão-alvo (domain/application/persistence/web), VO `Score`, exceções puras, sem `HttpStatus` no service.
- **Escopo (in):** Reorganizar pacotes; `Rating`/`ReadingList` como agregados; VO `Score` (range 1–5); `RateMangaUseCase`/`UpdateRating`/`RemoveRating`/reading-list use cases (pode ser **um** application service por entidade — proporcionalidade); recálculo `avgRating` síncrono via use case; converter exceções para puras; remover checagem solta.
- **Fora de escopo:** Mudar contratos HTTP (endpoints idênticos).
- **Arquivos:** `engagement/**`.
- **Flyway?** Não.
- **Testes:** os de 1.1 continuam verdes (rede de segurança); + testes de domínio do VO `Score` em JUnit puro.
- **DoD:** endpoints com comportamento idêntico; testes verdes; revisão confirma que o padrão é legível.
- **Tamanho:** M · **Depende de:** 1.1, 0.4

### [1.3] Remover `test-phase7.sh`
- **Objetivo:** Cobertura migrada → remover o bash.
- **Escopo (in):** Deletar o script; atualizar README/docs que o citem.
- **DoD:** suíte automatizada cobre o que o script cobria; script removido.
- **Tamanho:** S · **Depende de:** 1.2

> ◇ **MARCO (revisão de padrão):** revisar 1.2 com calma. Este é o template de todos os contextos seguintes — ajustar nomenclatura/estrutura aqui sai barato.

---

## 5. Epic 2 — `reading` · médio risco (R2 leitura assinada)

### [2.1] Rede de segurança: portar `test-phase6.sh`
- **Escopo (in):** Integração cobrindo `GET /reader/{volumeId}/url` (formato da URL, incremento de `view_count`, inserção de `ReadingHistory`), upsert de progresso, histórico, atalhos por mangá. `StorageClient` como **fake/mews** (sem GCS real); `Clock.fixed` para a expiração.
- **Arquivos:** `src/test/.../reading/*`.
- **Flyway?** Não.
- **DoD:** cenários do `test-phase6.sh` reproduzidos; script ainda não removido.
- **Tamanho:** M · **Depende de:** 0.4

### [2.2] Migrar `reading` para Clean Arch
- **Escopo (in):** Pacotes domain/application/persistence/web; `ReadingProgress` (upsert) e `ReadingHistory` simples; use cases de leitura; expiração de URL via `Clock`; exceções puras. Tratamento **simplificado** (proporcionalidade — sem agregado pesado).
- **Fora de escopo:** Mudar expirações ou contrato.
- **Arquivos:** `reading/**` (atual `reader/**`).
- **Flyway?** Não.
- **Testes:** 2.1 verdes + unitários de qualquer regra extraída.
- **DoD:** comportamento idêntico; testes verdes.
- **Tamanho:** M · **Depende de:** 2.1

### [2.3] Remover `test-phase6.sh`
- **Tamanho:** S · **Depende de:** 2.2

> ◇ **MARCO PROD M1** (após deploy): testar leitura em produção (abrir, virar página, progresso salvo, expiração+renovação da URL).

---

## 6. Epic 3 — `identity` (fusão auth+user) · alto risco (R3, R5, R7)

### [3.1] Rede de segurança: portar `test-phase2.sh` + refresh rotation + 2FA
- **Escopo (in):** Integração/`@WebMvcTest` cobrindo: registro (rate limit/captcha desligável em teste), login com/sem 2FA, aprovação `PENDING→ACTIVE`, mudança de role/status, **rotação de refresh token** (token antigo morre), reset de senha com/sem TOTP. `EmailSender` fake; `Clock.fixed`.
- **Arquivos:** `src/test/.../identity/*`.
- **Flyway?** Não.
- **DoD:** cenários do `test-phase2.sh` + R5/R7 reproduzidos; script ainda não removido.
- **Tamanho:** L · **Depende de:** 0.4

### [3.2] Fundir `auth`+`user` em `identity` + esqueleto de camadas
- **Escopo (in):** Mover `auth/**` e `user/**` para `identity/**`; criar subpastas `application/{authentication,account,admin}` (legibilidade); manter comportamento.
- **Fora de escopo:** Enriquecer domínio (vem em 3.3).
- **Arquivos:** `auth/**`, `user/**` → `identity/**`; imports em quem referencia (`manga`, `admin`).
- **Flyway?** Não.
- **Testes:** 3.1 verdes.
- **DoD:** compila, sobe, testes verdes; pacote `identity` com subpastas.
- **Tamanho:** L (move grande, risco baixo) · **Depende de:** 3.1

### [3.3] `User` rico + VOs (Email, Username, Quota)
- **Escopo (in):** Transições de status como métodos (`approve()`, `reject()`, `deactivate()`), com invariantes; VOs `Email`/`Username`/`Quota` (`canFit`/`remaining`); encapsular setters perigosos.
- **Arquivos:** `identity/domain/**`, use cases que mutavam `User` via setter.
- **Flyway?** Não.
- **Testes:** unitários puros das transições e dos VOs; 3.1 verdes.
- **DoD:** mutação de `User` só por métodos de negócio; testes verdes.
- **Tamanho:** M · **Depende de:** 3.2

### [3.4] Exceções de identity puras + remover `HttpStatus` dos services
- **Escopo (in):** Converter exceções de `auth`/`user` para a base pura (ADR-33); RBAC só na borda; ownership por `actorId`.
- **Arquivos:** `identity/**`, handler.
- **Flyway?** Não.
- **Testes:** 3.1 verdes; status HTTP inalterados para o cliente.
- **DoD:** nenhum `HttpStatus` em `identity/application`/`domain`.
- **Tamanho:** M · **Depende de:** 3.3

### [3.5] Remover `test-phase2.sh`
- **Tamanho:** S · **Depende de:** 3.4

> ◇ **MARCO PROD M2** (após deploy): registro, aprovação, login com/sem 2FA, refresh automático, reset de senha — todos em produção.

---

## 7. Epic 4 — `manga` (catálogo + coleção + volumes) · MAIOR risco (R1, R6)

> Maior contexto e maior risco. Quebra a god-service `PrivateMangaService` (350 l) e toca o upload em 2 fases. Rede de segurança robusta primeiro.

### [4.1] Rede de segurança: portar `test-phase3/4/5.sh`
- **Escopo (in):** Integração cobrindo: tags/categorias; catálogo público (listagem paginada + tags via two-step, busca/filtro AND); **upload 2-fases** (upload-url + finalize, dedup por hash/número) público **e** privado; cota; `promote`; **fluxo de submissão** (submit→approve/reject). `StorageClient` fake + Testcontainers; sem GCS real.
- **Arquivos:** `src/test/.../manga/*`.
- **Flyway?** Não.
- **DoD:** cenários dos 3 scripts reproduzidos; scripts ainda não removidos.
- **Tamanho:** L · **Depende de:** 0.4

### [4.2] Migrar `tags` para Clean Arch
- **Escopo (in):** `Tag`/`TagCategory` como reference data; use cases admin; camadas; exceções puras. Baixo risco — primeiro passo dentro de `manga`.
- **Arquivos:** `manga/**` (parte tags).
- **Flyway?** Não · **Testes:** 4.1 (tags) verdes · **DoD:** comportamento idêntico · **Tamanho:** M · **Depende de:** 4.1

### [4.3] `Manga` agregado rico + `Volume` interno + VOs
- **Escopo (in):** `Manga` raiz com `addVolume`/`removeVolume` (invariante de número único dentro do agregado), VOs `Slug`/`VolumeNumber`/`FileHash`, `promoteToPublic()`/`submitForApproval()`/`approve()`/`reject()` como métodos **sem receber `User`** (ADR-35); encapsular setters.
- **Fora de escopo:** Reescrever os services ainda (vem 4.4–4.6).
- **Arquivos:** `manga/domain/**`.
- **Flyway?** Não.
- **Testes:** unitários puros dos métodos do agregado e VOs; 4.1 verdes.
- **DoD:** invariantes no domínio, testáveis sem Spring.
- **Tamanho:** M · **Depende de:** 4.1

### [4.4] Quebrar `PrivateMangaService` em use cases (coleção privada)
- **Escopo (in):** `CreatePrivateMangaUseCase`, `GenerateVolumeUploadUrlUseCase`, `FinalizeVolumeUseCase`, `UpdatePrivateMangaUseCase`, `DeletePrivateMangaUseCase`, `DeleteVolumeUseCase`; `QuotaService`/VO `Quota`; ownership por `actorId`; mapper único do agregado (remove duplicação com `VolumeService`).
- **Arquivos:** `manga/application/**`, `manga/web/PrivateMangaController.java`.
- **Flyway?** Não.
- **Testes:** use cases com mocks; 4.1 (privado, upload) verdes.
- **DoD:** `PrivateMangaService` deixa de existir como god-service; endpoints idênticos.
- **Tamanho:** L · **Depende de:** 4.3

### [4.5] Extrair use cases de submissão/promoção (admin-facing para fora)
- **Escopo (in):** `SubmitForApprovalUseCase`, `ReviewSubmissionUseCase` (approve/reject), `PromoteMangaUseCase` (ver §7 da arquitetura-alvo); notificação por e-mail via `EmailSender`; mover os métodos admin para fora de "PrivateManga".
- **Arquivos:** `manga/application/**`, `manga/web/**`, `admin/web/AdminSubmissionController.java` (rewire).
- **Flyway?** Não.
- **Testes:** 4.1 (submissão/promote) verdes; unitários das regras de conflito.
- **DoD:** caminhos `promote` e `submit→approve/reject` idênticos em comportamento, agora em use cases dedicados.
- **Tamanho:** L · **Depende de:** 4.4

### [4.6] Migrar catálogo público + volumes públicos para use cases
- **Escopo (in):** `MangaService`/`VolumeService` → use cases (`CatalogQuery`, `GetMangaUseCase`, `UploadPublicVolume`/`FinalizePublicVolume`, `DeletePublicVolume`); preservar two-step fetch (ADR-16) e resolução slug/UUID (ADR-13); mapper único.
- **Arquivos:** `manga/application/**`, `manga/web/{MangaController,VolumeController}.java`.
- **Flyway?** Não.
- **Testes:** 4.1 (público+volumes) verdes; `@DataJpaTest` para queries/specification.
- **DoD:** listagem/detalhe/upload público idênticos.
- **Tamanho:** L · **Depende de:** 4.5

### [4.7] Remover checagem de role solta no `manga`
- **Escopo (in):** Eliminar `assertCanModify`/`if (role==...)` dos services; ownership por `actorId`, RBAC por `@PreAuthorize` consistente em todos os endpoints (ADR-35).
- **Arquivos:** `manga/**`, controllers.
- **Flyway?** Não · **Testes:** 4.1 (autorização) verdes · **DoD:** zero role-check fora da borda/ownership · **Tamanho:** M · **Depende de:** 4.6

### [4.8] Publicar `DeletePrivateCollectionForUserUseCase` (para o Epic 5)
- **Contexto/Objetivo:** Dar ao contexto `manga` um caso de uso público que apaga a coleção privada de um usuário, para o job de inatividade chamar **sem** importar internals de `manga` (ADR-35, Ajuste 2 da Fase 2).
- **Escopo (in):** `DeletePrivateCollectionForUserUseCase(UUID userId)` em `manga/application/admin` (ou `application/maintenance`).
  - **Fronteira transacional:** a deleção das linhas de banco (mangás privados + volumes do usuário) ocorre **numa transação** dentro deste use case. O método **retorna a lista de object names do GCS** a apagar (não apaga dentro da tx).
  - **I/O externo:** a deleção dos arquivos no GCS (`StorageClient.delete`) é feita **após o commit**, fora da transação, de forma best-effort/idempotente — órfãos são tolerados (consistente com ADR-24). Falha de GCS **não** faz rollback do banco.
  - **Cross-contexto:** chamado pelo `RunInactivityUseCase` de `identity`; **um usuário por chamada**, processados independentemente (falha num usuário não afeta os outros). Não há transação compartilhada entre `identity` e `manga`.
- **Arquivos:** `manga/application/**` (novo use case).
- **Flyway?** Não.
- **Testes:** integração: deleta DB + retorna object names; falha simulada de GCS não reverte DB.
- **DoD:** use case público disponível e testado; nenhum acoplamento reverso `manga→identity`.
- **Tamanho:** M · **Depende de:** 4.4

### [4.9] Remover `test-phase3/4/5.sh`
- **Tamanho:** S · **Depende de:** 4.6, 4.7

> ◇ **MARCO PROD M3** (deploys incrementais): testar em produção upload público (COLLABORATOR), upload privado + cota, `promote`, e submissão→aprovação. Recomendado validar após 4.6 e novamente após 4.8.

---

## 8. Epic 5 — inatividade + admin · alto risco (R4, bugs B1/B2, cross-contexto)

> Só começa depois de `identity` (Epic 3) e do use case 4.8 prontos, pois o job cruza os dois contextos.

### [5.1] Rede de segurança: portar `test-phase8.sh`
- **Escopo (in):** Integração com `Clock.fixed` + Testcontainers cobrindo: usuário > 75 dias recebe aviso; > 90 dias é desativado + coleção privada apagada; `GET /admin/dashboard`. `EmailSender` fake.
- **Arquivos:** `src/test/.../identity/inactivity/*`, `admin/*`.
- **Flyway?** Não.
- **DoD:** cenários do `test-phase8.sh` reproduzidos de forma determinística (sem depender de relógio real); script ainda não removido.
- **Tamanho:** M · **Depende de:** 0.4

### [5.2] Extrair `InactivityPolicy` (domínio puro)
- **Escopo (in):** `InactivityPolicy.decide(lastAccessAt, now) → NONE|WARN|DEACTIVATE`; testes unitários dos limiares 75/90 sem Spring.
- **Arquivos:** `identity/domain/InactivityPolicy.java` + teste.
- **Flyway?** Não · **Testes:** unitários puros dos limiares · **DoD:** política isolada e testada · **Tamanho:** S · **Depende de:** 5.1

### [5.3] Refatorar o job: use case + corrigir B1/B2 + cross-contexto
- **Contexto/Objetivo:** `RunInactivityUseCase` usando a política, **corrigindo os dois bugs**.
- **Escopo (in):**
  - **Corrige B1** (@Transactional ineficaz por self-invocation): a desativação de cada usuário fica num bean/método transacional próprio, invocado externamente (proxy AOP válido).
  - **Corrige B2** (paginação sobre conjunto mutável): iterar de forma estável — ex.: selecionar apenas usuários **já além do limiar** por query, ou paginar por id/keyset, sem reconsultar `ACTIVE` por offset enquanto muda status.
  - Usa `Clock` (ADR-36) e chama `DeletePrivateCollectionForUserUseCase` (4.8) para a coleção; GCS apagado fora da tx.
- **Fora de escopo:** Mudar limiares (75/90) ou o contrato do endpoint/scheduler.
- **Arquivos:** `identity/application/admin/RunInactivityUseCase.java` (ex-`InactivityJob`), `admin/web/JobController.java` (rewire), `@Scheduled`.
- **Flyway?** Não.
- **Testes:** 5.1 verdes + caso explícito provando que **nenhum usuário é pulado** num lote onde vários são desativados (regressão de B2) e que a falha numa desativação não afeta as outras (B1).
- **DoD:** B1 e B2 corrigidos com teste de regressão; comportamento de aviso/desativação preservado.
- **Tamanho:** L · **Depende de:** 5.2, 4.8, 3.4

### [5.4] `admin` como casca
- **Escopo (in):** `DashboardController`/`JobController`/`AdminSubmissionController` apenas orquestram use cases dos contextos; `DashboardService` reduzido a query/projeção. Sem domínio em `admin`.
- **Arquivos:** `admin/**`.
- **Flyway?** Não · **Testes:** `@WebMvcTest` dos controllers · **DoD:** `admin` sem regra de negócio própria · **Tamanho:** M · **Depende de:** 5.3

### [5.5] Remover `test-phase8.sh`
- **Tamanho:** S · **Depende de:** 5.3

> ◇ **MARCO PROD M4** (após deploy): num usuário de teste, ajustar `last_access_at` para >90 dias e disparar `POST /admin/jobs/inactivity`; conferir desativação, e-mail e limpeza de arquivos no GCS.

---

## 9. Epic 6 — Limpeza final + frontend leve · baixo risco

### [6.1] Remover `DomainException(HttpStatus)` legado
- **Escopo (in):** Quando nenhum contexto mais usar a exceção legada, removê-la; handler só com exceções puras.
- **Arquivos:** `shared/exception/**`, handler.
- **Flyway?** Não · **Testes:** suíte completa verde · **DoD:** zero `HttpStatus` fora de `web` · **Tamanho:** S · **Depende de:** Epics 1–5

### [6.2] Frontend: camada `api/` + `types/`
- **Escopo (in):** Extrair chamadas `axios` das telas para `src/api/*Api.ts` por contexto; tipos de request/response em `src/types/`; manter pages/components/store. **Sem Clean Arch** (ADR-31).
- **Arquivos:** `frontend/src/api/**`, `frontend/src/types/**`, `pages/**` (substituir chamadas diretas).
- **Flyway?** Não · **Testes:** build do front + smoke manual · **DoD:** telas não chamam `axios` cru; comportamento idêntico · **Tamanho:** M · **Depende de:** —

### [6.3] Saneamento de OpenAPI/Swagger e restos
- **Escopo (in):** Revisar anotações/descrições desatualizadas; remover código morto remanescente.
- **Tamanho:** S · **Depende de:** 6.1

---

## 10. Observações finais

- **Flyway:** nenhuma issue exige migration — a arquitetura-alvo preserva o schema. Se durante a execução surgir necessidade real (ex.: índice para nova query de keyset em 5.3), criar `V21__...` seguindo a convenção, e documentar na issue.
- **Tamanho total:** ~24 issues em 6 epics. Nenhuma é "big-bang"; cada PR mantém o sistema funcionando.
- **Ordem inegociável:** rede de segurança (`.1` de cada epic) **antes** da refatoração; remoção do bash **depois** da cobertura automatizada.
- **Fase 4** (documentação viva: `CLAUDE.md`, `docs/arquitetura.md`, `docs/convencoes.md`, `docs/glossario-dominio.md`, `CONTRIBUTING.md` + `README.md` reescrito + templates `.github/`) entra **após** a execução estabilizar o padrão — idealmente os docs são atualizados incrementalmente a cada epic, e consolidados ao final.

### Backlog (achados durante o Epic 6, fora do escopo — não fazer sem issue própria)

Migrado para [`docs/BACKLOG.md`](../BACKLOG.md).

---

**➡️ Próximo passo:** aguardando sua validação do roadmap. Aprovado, posso (a) iniciar a execução pela issue **[0.1]**, ou (b) já gerar os entregáveis da **Fase 4** (documentação viva) — me diga a preferência. Recomendo começar pela execução do Epic 0 + Epic 1 (piloto) e só então escrever a Fase 4 com o padrão já validado na prática.
