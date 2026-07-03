# CLAUDE.md — Burūna

> Contexto de trabalho para o Claude Code. Este projeto está em refatoração de
> monolito modular para Clean Architecture + DDD. A arquitetura ABAIXO é a atual
> e correta. Os arquivos docs/buruna_architecture.md e docs/buruna_roadmap_mvp.md
> descrevem o estado ANTIGO (pré-refatoração) — são histórico, NÃO a verdade atual.
> A verdade atual vive em docs/refactor/ (01-analise, 02-arquitetura-alvo, 03-roadmap,
> ADR-31..39).

## Acordo de trabalho

Você é um engenheiro sênior nas stacks do projeto. Sempre: SOLID, Clean Code, DDD
com domínio rico. Sempre pergunte na dúvida em vez de assumir. NUNCA faça
over-engineering — prefira a solução mais simples que resolve e sinalize quando uma
abstração não se justifica. Priorize legibilidade: o projeto recebe contribuições da
comunidade.

## Stacks

Backend: Java 21, Spring Boot 3.x, PostgreSQL, Flyway, JWT+Refresh, BCrypt, GCS,
Testcontainers, ArchUnit. Frontend: React 18 + TypeScript, shadcn/ui, Tailwind, Axios.

## Arquitetura — Clean Architecture por bounded context

Contextos: identity (auth+user fundidos), manga (catálogo+coleção+volumes+tags),
reading (leitor+progresso+histórico), engagement (ratings+reading-list), admin (casca).

Cada contexto:

    contexto/
    ├── domain/         Agregados ricos (invariantes em métodos, SEM setter público),
    │                   Value Objects, exceções de domínio puras (sem HttpStatus)
    ├── application/    Use cases focados (um por intenção) + DTOs
    ├── persistence/    Interfaces Spring Data JPA
    └── web/            Controllers — extraem actorId/@PreAuthorize e delegam ao use case

NÃO existe mais o padrão antigo controller/service/repository. Não crie `service/`,
não use `repository/` como nome de pacote (é `persistence/`), não faça entidade anêmica.

## Regra de dependência (guardada por ArchUnit em ArchitectureTest)

- domain/application de um contexto NÃO importa domain/persistence de outro contexto.
- Cross-contexto SÓ via application (use case público) do outro contexto.
- Referências cross-contexto por UUID — nunca a entidade de outro contexto.
- RBAC na borda (@PreAuthorize no controller); ownership por actorId na application.
- Exceções de domínio são puras (DomainErrorType); tradução para HTTP SÓ no
  GlobalExceptionHandler. LegacyHttpDomainException é legado em remoção.
- Proibido @Query(nativeQuery=true) cross-contexto e JOIN entre tabelas de contextos
  diferentes — passe por use case público (ADR-39).

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

- Domínio testável em JUnit puro, sem subir Spring.
- Integração com @SpringBootTest + Testcontainers; StorageClient/EmailSender fake.
- @DataJpaTest para queries; @WebMvcTest para controllers.
- AAA; nomenclatura should[Resultado]_when[Condicao].
- Rede de segurança (portar o bash equivalente) ANTES de refatorar cada contexto;
  remover o bash só depois da cobertura automatizada.

## Padrões do projeto

- IDs UUID; created_at/updated_at; soft delete deleted_at onde aplicável.
- Paginação Pageable (offset/page). Enums em inglês. Migrations V{n}__{desc}.sql.
- Erro padronizado {status,error,message,path,timestamp} (ErrorResponse).
- Arquivos no GCS com nome ofuscado (UUID).

## Forma de trabalhar

- Código pronto para produção: sem TODO/stub.
- Commit por issue, SEM push (a menos que pedido).
- Informe nome + caminho dos arquivos que tocar.
- Sinalize dependências não implementadas antes de gerar código.
- Na dúvida, pergunte.