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

Backend: Java 21, Spring Boot 3.x, PostgreSQL, Flyway, JWT+Refresh, BCrypt, GCS,
Testcontainers, ArchUnit. Frontend: React 18 + TypeScript, shadcn/ui, Tailwind, Axios.

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

Pirâmide (domínio JUnit puro / @DataJpaTest / @WebMvcTest / integração
@SpringBootTest+Testcontainers com StorageClient/EmailSender fake), AAA, nomenclatura
`should[Resultado]_when[Condicao]`. Rede de segurança (portar o bash equivalente) ANTES
de refatorar cada contexto; remover o bash só depois da cobertura automatizada.
Detalhes: [docs/TESTING.md](docs/TESTING.md).

## Padrões do projeto

IDs UUID; created_at/updated_at; soft delete deleted_at onde aplicável. Paginação
Pageable (offset/page). Enums em inglês. Migrations V{n}__{desc}.sql. Erro
padronizado {status,error,message,path,timestamp} (ErrorResponse). Arquivos no GCS com
nome ofuscado (UUID). Modelo de dados completo: [docs/DATABASE.md](docs/DATABASE.md).

## Setup local e comandos

### Pré-requisitos
- Java 21, Docker (Docker Compose), Node 20+.

### Subir o Postgres
```
docker compose up -d postgres
```
Exposto em `localhost:5433` (mapeado do `5432` do container — ver `docker-compose.yml`).
As credenciais/nome do banco vêm do `.env` na raiz (`DB_NAME`/`DB_USER`/`DB_PASSWORD`,
lidos pelo `docker compose` automaticamente).

### Rodar o backend local
Variáveis **obrigatórias** (sem default em `application.yml`) — só estas 7:
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `GCS_BUCKET_NAME`,
`GCS_CREDENTIALS_PATH`, `ADMIN_EMAIL`.

- `GCS_BUCKET_NAME`/`GCS_CREDENTIALS_PATH` **não são usados** no profile `local`
  (`GcsConfig` é `@Profile("!local")`, o bean real de GCS não sobe) — mas precisam de
  **qualquer valor** (ex.: `dummy`) porque `AppProperties` (`@ConfigurationProperties`)
  faz bind **eager** de todo `app.*`, inclusive o que não é usado no profile ativo.
- Demais variáveis de `application.yml` têm default e são **opcionais** para rodar
  local: `JWT_EXPIRATION`, `REFRESH_TOKEN_EXPIRATION`, `MAX_FILE_SIZE_MB`,
  `RATE_LIMIT_REGISTER_PER_HOUR`/`LOGIN_PER_HOUR`/`FEEDBACK_PER_HOUR`/`FORGOT_PASSWORD_PER_HOUR`,
  `RESEND_API_KEY`, `APP_FRONTEND_URL`, `APP_CORS_ALLOWED_ORIGIN`, `APP_JOBS_SECRET`,
  `APP_MAIL_FROM`, `SWAGGER_ENABLED`, `PORT`.
- `HCAPTCHA_SECRET` também tem default vazio → captcha desligado local
  (`CaptchaService` pula a verificação quando `app.hcaptcha.secret` está vazio).

O profile `local` ativa `LocalStorageClient` (`LocalStorageConfig`, `@Profile("local")`),
que exige `app.storage.local.path`. O default em `application-local.yml`
(`/app/storage`) é o caminho **dentro do container** do `docker-compose` — não existe
no host, então rodando fora do Docker é obrigatório sobrescrever via argumento:

```
DB_URL=jdbc:postgresql://localhost:5433/<DB_NAME do .env> \
DB_USER=<DB_USER do .env> \
DB_PASSWORD=<DB_PASSWORD do .env> \
JWT_SECRET=<qualquer-string-para-dev> \
ADMIN_EMAIL=<email-do-admin-seed> \
GCS_BUCKET_NAME=dummy \
GCS_CREDENTIALS_PATH=dummy \
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--app.storage.local.path=/tmp/buruna-storage
```

### Rodar o frontend local
```
npm install && npm run dev
```
(dentro de `frontend/`). O `vite.config.ts` já tem proxy `/api` → `http://localhost:8080`
— nenhuma variável de ambiente extra é necessária para falar com o backend local.

### Rodar os testes
- Backend: `./mvnw clean test` (dentro de `backend/`; sobe Testcontainers/Postgres —
  Docker precisa estar rodando). Estado atual: 289 testes verdes.
- Frontend: `npm run build` (typecheck + build via `tsc -b && vite build`).

### Notas de dev local
- E-mail é `@Async` (`EmailService`); sem `RESEND_API_KEY`, `ResendEmailSender` só loga
  `[EMAIL SKIP]` e retorna — não bloqueia o fluxo (registro, aprovação, reset de senha).
- Upload de volume depende de storage: `GcsStorageClient` em prod, `LocalStorageClient`
  em `local` (arquivos servidos via `/local-storage/**`, `permitAll` só em dev). Pendência
  conhecida: smoke test local do fluxo de upload em 2 fases não foi verificado nesta sessão.

## Forma de trabalhar

- Código pronto para produção: sem TODO/stub.
- Commit por issue, SEM push (a menos que pedido).
- Informe nome + caminho dos arquivos que tocar.
- Sinalize dependências não implementadas antes de gerar código.
- Na dúvida, pergunte.
