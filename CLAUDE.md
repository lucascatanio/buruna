# CLAUDE.md — Burūna

> Contexto de trabalho para o Claude Code. A refatoração de monolito modular para
> Clean Architecture + DDD (Epics 0-6) está **concluída**. A arquitetura abaixo é o
> estado atual. Documentação completa em `docs/` — este arquivo é um resumo acionável,
> não a fonte única; aponta para os docs em vez de duplicar. `docs/legacy/` e os
> documentos de processo em `docs/refactor/` são histórico, não a verdade atual.

## Acordo de trabalho

Você é um engenheiro sênior nas stacks do projeto. Sempre: SOLID, Clean Code, DDD
com domínio rico. Sempre pergunte na dúvida em vez de assumir. NUNCA faça
over-engineering — prefira a solução mais simples que resolve e sinalize quando uma
abstração não se justifica. Priorize legibilidade: o projeto recebe contribuições da
comunidade.

## Stacks

Backend: Java 21, Spring Boot 3.4.3, PostgreSQL 16, Flyway, JWT + Refresh Token,
BCrypt, 2FA TOTP, Google Cloud Storage, Testcontainers e ArchUnit.

Frontend: React 19, TypeScript 5.9, Vite, shadcn/ui, Tailwind CSS, Axios e Zustand.

Infra/Deploy: Docker, GitHub Actions, Google Cloud Run, Google Cloud Storage,
Secret Manager e Cloud Scheduler.

## Arquitetura

Clean Architecture por bounded context: `identity` (auth+user fundidos), `manga`
(catálogo+coleção+volumes+tags), `reading` (leitor+progresso+histórico), `engagement`
(ratings+reading-list), `admin` (casca). Cada contexto segue
`domain/ → application/ → persistence/ → web/`. NÃO existe mais o padrão antigo
`controller/service/repository`. Não crie `service/`, não use `repository/` como nome
de pacote (é `persistence/`), não faça entidade anêmica.

Camadas, fluxos de usuário atualizados e diagrama completo:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Decisões e tradeoffs de cada escolha:
[docs/adr/](docs/adr/) (ADR-01 a ADR-39).

## Regra de dependência (guardada por ArchUnit em ArchitectureTest)

- domain/application de um contexto NÃO importa domain/persistence de outro contexto.
- Cross-contexto SÓ via application (use case público) do outro contexto.
- Referências cross-contexto por UUID — nunca a entidade de outro contexto.
- RBAC na borda (@PreAuthorize no controller); ownership por actorId na application.
- Exceções de domínio são puras (DomainErrorType); tradução para HTTP SÓ no
  GlobalExceptionHandler.
- Proibido @Query(nativeQuery=true) cross-contexto e JOIN entre tabelas de contextos
  diferentes — passe por use case público (ADR-39).
- A camada web/ é isenta da regra, de propósito: controller recebe identity.domain.User
  via @AuthenticationPrincipal e extrai user.getId() antes de delegar. O ArchUnit só
  guarda domain/ e application/ — isso não é violação.

## Domínio (DDD)

Regra de negócio mora no agregado, como método com invariante
(ex.: manga.promoteToPublic(), user.approve(), rating via Score VO). Domain service
só quando a lógica não pertence a nenhuma entidade e depende de repositório/serviço
externo (ex.: QuotaService, SlugAllocator). Não crie por padrão. Domínio rico anotado
com JPA na mesma classe (ADR-32) — sem separar entidade-de-domínio de entidade-JPA.

## Regras obrigatórias

- Controller nunca contém lógica de negócio.
- Use case nunca retorna entidade JPA — sempre DTO.
- Repository é sempre interface (Spring Data).
- Injeção sempre via construtor.
- Nunca retorne null — Optional ou exceção de domínio.
- Nunca hardcode secrets — variáveis de ambiente.

## Testes

AAA, nomenclatura `should[Resultado]_when[Condicao]`. Rede de segurança automatizada
ANTES de refatorar qualquer contexto — nunca refatore sem cobertura do comportamento
atual. Pirâmide completa e escolha do nível de teste:
[docs/TESTING.md](docs/TESTING.md).

## Padrões do projeto

IDs UUID; created_at/updated_at; soft delete deleted_at onde aplicável. Paginação
Pageable (offset/page). Enums em inglês. Migrations V{n}__{desc}.sql. Erro
padronizado {status,error,message,path,timestamp} (ErrorResponse). Arquivos no GCS com
nome ofuscado (UUID). Modelo de dados completo: [docs/DATABASE.md](docs/DATABASE.md).

## Comandos de verificação

- Backend: `./mvnw clean test` (dentro de `backend/`; sobe Testcontainers, Docker precisa
  estar rodando).
- Frontend: `npm run build` (dentro de `frontend/`; typecheck + build).

Setup local completo — pré-requisitos, subir o Postgres, variáveis de ambiente
obrigatórias × opcionais, rodar backend e frontend, notas de storage e e-mail:
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), que é a fonte única e foi validada
empiricamente. Não reproduza esses comandos aqui.

## Forma de trabalhar

- Código pronto para produção: sem TODO/stub.
- Commit por issue, SEM push (a menos que pedido).
- Informe nome + caminho dos arquivos que tocar.
- Sinalize dependências não implementadas antes de gerar código.
- Na dúvida, pergunte.
