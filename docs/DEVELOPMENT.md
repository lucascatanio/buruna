# Desenvolvimento local

> Esta seção é a fonte única de verdade sobre setup local — validada empiricamente.
> Não invente comandos ou valores novos sem antes verificar contra o código
> (`application.yml`, `application-local.yml`, `docker-compose.yml`).

## Pré-requisitos

- Java 21, Docker (Docker Compose), Node 22+.

## Subir o Postgres

```
docker compose up -d postgres
```
Exposto em `localhost:5433` (mapeado do `5432` do container — ver `docker-compose.yml`).
As credenciais/nome do banco vêm do `.env` na raiz (`DB_NAME`/`DB_USER`/`DB_PASSWORD`,
lidos pelo `docker compose` automaticamente).

## Rodar o backend local

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

## Rodar o frontend local

```
npm install && npm run dev
```
(dentro de `frontend/`). O `vite.config.ts` já tem proxy `/api` → `http://localhost:8080`
— nenhuma variável de ambiente extra é necessária para falar com o backend local.

## Rodar os testes

- Backend: `./mvnw clean test` (dentro de `backend/`; sobe Testcontainers/Postgres —
  Docker precisa estar rodando). Estado atual: 289 testes verdes.
- Frontend: `npm run build` (typecheck + build via `tsc -b && vite build`).

Convenções de teste (pirâmide, AAA, nomenclatura): [TESTING.md](TESTING.md).

## Notas de dev local

- E-mail é `@Async` (`EmailService`); sem `RESEND_API_KEY`, `ResendEmailSender` só loga
  `[EMAIL SKIP]` e retorna — não bloqueia o fluxo (registro, aprovação, reset de senha).
- Upload de volume depende de storage: `GcsStorageClient` em prod, `LocalStorageClient`
  em `local` (arquivos servidos via `/local-storage/**`, `permitAll` só em dev). Pendência
  conhecida: smoke test local do fluxo de upload em 2 fases não foi verificado nesta sessão.
