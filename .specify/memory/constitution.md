# Constituição do Burūna

Regras que valem para todo código, spec e plano do projeto. O `/speckit-plan` confere cada
plano contra esta constituição (seção *Constitution Check*) e o `/speckit-analyze` aponta
conflitos. Quando esta constituição e outro documento discordam, vale a constituição, e o
outro documento é corrigido.

Os números `ADR-NN` citados são as decisões de origem, resumidas em
[docs/ARCHITECTURE.md §7](../../docs/ARCHITECTURE.md#7-registro-de-decisões). O texto
completo de cada uma continua no histórico do git.

## Princípios

### I. Clean Architecture por bounded context

O backend é um monolito modular (ADR-01) com seis contextos: `identity`, `work`, `reading`,
`engagement`, `sourcing` e `admin`. Fora deles ficam `shared/` (infraestrutura comum) e
`feedback/` (módulo utilitário sem camadas).

- Cada contexto tem `domain/ → application/ → persistence/ → web/`, e a dependência aponta
  só para dentro (ADR-31).
- Não existem pacotes `service/` nem `repository/`. Repositório mora em `persistence/`.
- Repositório é interface Spring Data, sem porta própria por cima (ADR-37).
- DTO que um use case recebe ou devolve mora na `application/`. Na `web/` fica só o que o
  controller usa sozinho.
- Contexto quase-CRUD pode ter um único application service. Proporcionalidade vale mais
  que simetria.
- O frontend não segue Clean Architecture: só `api/` (chamadas tipadas por contexto) e
  `types/` (contratos espelhando os DTOs).

### II. Fronteiras entre contextos são testadas, não combinadas

O `ArchitectureTest` (ArchUnit) falha o build quando uma destas regras é quebrada:

- `domain/` e `application/` de um contexto **não** importam `domain/` nem `persistence/` de
  outro.
- Cross-contexto **só** pela `application` pública do outro contexto (ADR-35, ADR-39).
- Referência a outro contexto é por **UUID** (`actorId`, `ownerId`, `workId`), nunca a
  entidade dele. Comando que cruza a fronteira usa tipos simples.
- `@Query(nativeQuery = true)` é proibido em `persistence/`. `JOIN` ou JPQL que cite tabela
  ou entidade de outro contexto também é proibido. Esse último caso o ArchUnit não vê, e a
  revisão de PR cobra.
- A `web/` é isenta de propósito: o controller recebe `identity.domain.User` via
  `@AuthenticationPrincipal` e extrai `user.getId()` antes de delegar.

### III. Domínio rico

- Regra de negócio é método com invariante no agregado (`work.promoteToPublic()`,
  `user.approve()`), não setter chamado de fora. Sem `@Setter` público em campo com
  invariante (ADR-32).
- Uma classe por agregado, anotada com JPA. Não existe entidade de domínio separada da de
  persistência (ADR-32).
- Value Object só onde há regra de verdade (`Slug`, `Score`, `Quota`, `Language`,
  `ChapterNumber`, `VolumeObjectName`, ...). Não embrulhe toda string (ADR-34).
- Domain service só quando a lógica não pertence a nenhuma entidade e depende de
  repositório ou serviço externo (`QuotaService`, `SlugAllocator`). Não crie por padrão.
- Exceção de domínio é pura: estende `DomainException` com um `DomainErrorType`, sem
  `HttpStatus`. A tradução para HTTP acontece só no `GlobalExceptionHandler` (ADR-33).
- Tempo vem de `java.time.Clock` injetado, nunca de `now()` solto (ADR-36).

### IV. Segurança por padrão

- **RBAC na borda** (`@PreAuthorize` no controller). **Posse na application**, por
  `actorId`. O agregado nunca recebe o `User` de outro contexto (ADR-35).
- Arquivo nunca é público. O bucket é privado, o acesso é por URL assinada V4 com expiração
  curta e o nome do objeto é ofuscado por UUID (ADR-07).
- O caminho de um objeto vem do backend, vinculado ao recurso, e é validado por VO com
  regex ancorada no finalize. Nunca confie num `objectName` vindo do cliente (ADR-40).
- Token de autenticação é guardado só como hash. O refresh vive em cookie `httpOnly`,
  `SameSite=Strict`, e o access token fica só em memória no front (ADR-41).
- Arquivo enviado ou baixado é tratado como hostil: tipo pelos magic bytes, limites sobre
  os bytes lidos, EPUB isolado pelo CSP (ADR-48, ADR-51). Quem mexe no CSP do `nginx.conf`
  lê a [SECURITY.md](../../SECURITY.md) antes.
- Segredo só por variável de ambiente ou Secret Manager. Nunca no código nem no repositório.

### V. Teste antes de mudar comportamento

- Pirâmide da [docs/TESTING.md](../../docs/TESTING.md): domínio em JUnit puro (a maioria),
  use case com Mockito, `@DataJpaTest`, `@WebMvcTest` e poucos `@SpringBootTest` com
  Testcontainers nos fluxos críticos (ADR-38).
- Estilo AAA. Nome `should[Resultado]_when[Condicao]`. Um arquivo de teste por classe
  testada.
- **Rede de segurança:** antes de refatorar um fluxo, já existe teste automatizado cobrindo
  o comportamento atual. Nunca refatore sem cobertura.
- Teste de regra de negócio que precisa de Spring é sinal de regra no lugar errado.
- Toda mudança de comportamento entra com teste equivalente. Concorrência relevante (lock,
  corrida) tem teste com transações reais em paralelo.

### VI. Simplicidade antes de abstração

- A solução mais simples que resolve. Abstração entra quando existe um motivo concreto
  hoje, não um hipotético (ADR-37, ADR-29).
- Antes de adicionar infraestrutura (fila, broker, serviço novo), justifique por que o que
  já existe não serve. O projeto roda no free tier do GCP, e custo fixo novo precisa de
  motivo (ADR-03, ADR-22, ADR-23).
- O código é legível para quem chega de fora: o projeto recebe contribuições da comunidade.
- Plano que precisa quebrar um princípio registra o porquê na tabela *Complexity Tracking*
  do `plan.md`. Sem isso, a violação não passa.

### VII. Trabalho longo fora da requisição e consistência explícita

- No Cloud Run a CPU é estrangulada fora da requisição, então não existe `@Async` nem
  `@Scheduled` (ADR-03).
- Trabalho longo (extrair páginas, baixar capítulos) vai para o Cloud Run Job
  `buruna-ingest`, disparado **depois do commit**. Agendamento é pelo Cloud Scheduler. No
  profile `local`, o mesmo trabalho roda na própria requisição (ADR-48).
- Use case chamado no `afterCommit` usa `REQUIRES_NEW`. Um `REQUIRED` ali entraria na
  transação que acabou de terminar.
- Processamento idempotente: reexecutar não duplica nem corrompe (ADR-48).
- Unicidade em código novo fica na aplicação, com lock pessimista na linha dona e consulta,
  sem `UNIQUE` no banco. O lock cobre só o passo final, nunca download ou conversão. Os
  `UNIQUE` antigos (slug, e-mail, username, progresso) ficam (ADR-53).

## Padrões técnicos

**Stack:** Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway, Testcontainers e ArchUnit no
backend. React 19, TypeScript 5.9, Vite, shadcn/ui, Tailwind, Axios e Zustand no frontend.
Docker, GitHub Actions, Cloud Run (serviço e Job), GCS, Pub/Sub, Secret Manager e Cloud
Scheduler na infra. Versão nova de uma dependência principal é decisão de plano, não de
passagem.

**Regras obrigatórias:**

- Controller não tem lógica de negócio.
- Use case nunca devolve entidade JPA, sempre DTO.
- Injeção sempre pelo construtor.
- Nunca devolva `null`: use `Optional` ou exceção de domínio.
- IDs UUID. `created_at`/`updated_at`. Soft delete (`deleted_at`) onde se aplica.
- Paginação por `Pageable` (offset/page, ADR-05). Enums em inglês.
- Migration `V{n}__{descricao}.sql`. Migration publicada nunca é editada.
- Erro HTTP sempre no formato `ErrorResponse {status, error, message, path, timestamp}`.
- Código pronto para produção: sem TODO nem stub.

## Fluxo de desenvolvimento

1. **Issue primeiro.** Toda mudança nasce de uma issue no GitHub.
2. **Spec para o que muda comportamento.** `/speckit-specify` (o quê e o porquê, sem
   tecnologia), `/speckit-clarify` se houver decisão em aberto, `/speckit-plan` (o como,
   conferido contra esta constituição), `/speckit-tasks`, `/speckit-analyze`,
   `/speckit-implement`. A pasta fica em `specs/NNN-nome/` e cita a issue.
3. **Sem spec:** correção de uma linha, bump de dependência sem mudança de API e ajuste de
   texto ou doc vão direto para PR. Na dúvida, escreva a spec.
4. **Decisões:** a decisão de uma feature fica no `research.md` dela. Se virar regra para o
   projeto todo, entra aqui (emenda) ou como linha nova em
   [ARCHITECTURE.md §7](../../docs/ARCHITECTURE.md#7-registro-de-decisões). Não existem mais
   ADRs avulsos.
5. **Docs de referência acompanham o código.** Mudou fluxo, schema, deploy, setup ou
   segurança? O mesmo PR atualiza `docs/ARCHITECTURE.md`, `docs/DATABASE.md`,
   `docs/DEPLOYMENT.md`, `docs/DEVELOPMENT.md` ou `SECURITY.md`. Esses documentos descrevem
   o sistema como ele é hoje. Regra não é repetida neles: eles apontam para cá.
6. **Branch e PR:** branch a partir de `dev`, PR para `dev`, título e commits em
   `tipo(escopo): descrição`. Um commit por unidade lógica, squash no merge (detalhes no
   [CONTRIBUTING.md](../../CONTRIBUTING.md)).
7. **Portões antes do PR:** `./mvnw clean test` verde em `backend/` (inclui
   `ArchitectureTest`), `npm run build` verde em `frontend/` quando o front muda, e o
   checklist do template de PR marcado.

## Governança

- Esta constituição é a fonte única das regras. `CLAUDE.md`, `CONTRIBUTING.md` e os docs de
  referência apontam para ela em vez de repetir.
- Emenda é um PR que muda este arquivo, explica o motivo e ajusta o que deixou de cumprir a
  regra (ou abre issue para isso).
- Versão semântica: MAJOR quando um princípio é removido ou redefinido, MINOR quando um
  princípio ou seção entra ou cresce, PATCH para redação.
- A revisão de todo PR confere conformidade. Exceção aceita fica registrada no
  *Complexity Tracking* do plano da feature.

**Versão**: 1.0.0 | **Ratificada**: 2026-11-15 | **Última emenda**: 2026-11-15
