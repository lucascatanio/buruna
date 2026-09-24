# Desenvolvimento local

> Esta seção é a fonte única de verdade sobre setup local — validada empiricamente.
> Não invente comandos ou valores novos sem antes verificar contra o código
> (`application.yml`, `application-local.yml`, `docker-compose.yml`).

## Pré-requisitos

- Java 21, Docker (Docker Compose), Node 22+.

O backend exige **Java 21** (`<java.version>21</java.version>` no `pom.xml`), e não há
toolchain configurado: o Maven usa o `java` que estiver no `PATH`. Se o seu for outro,
o build falha com `error: release version 21 not supported` e é preciso apontar o
`JAVA_HOME` explicitamente:

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw clean test
```

(o caminho varia por distribuição — confira com `ls /usr/lib/jvm/`.)

## Subir o Postgres

```
docker compose up -d postgres
```
Exposto em `localhost:5433` (mapeado do `5432` do container — ver `docker-compose.yml`).
As credenciais/nome do banco vêm do `.env` na raiz (`DB_NAME`/`DB_USER`/`DB_PASSWORD`,
lidos pelo `docker compose` automaticamente).

## Rodar o backend local

Variáveis **obrigatórias** (sem default em `application.yml`) — só estas 8:
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `GCS_BUCKET_NAME`,
`GCS_CREDENTIALS_PATH`, `ADMIN_EMAIL`, `APP_JOBS_SECRET`.

- `GCS_BUCKET_NAME`/`GCS_CREDENTIALS_PATH` **não são usados** no profile `local`
  (`GcsConfig` é `@Profile("!local")`, o bean real de GCS não sobe) — mas precisam de
  **qualquer valor** (ex.: `dummy`) porque `AppProperties` (`@ConfigurationProperties`)
  faz bind **eager** de todo `app.*`, inclusive o que não é usado no profile ativo.
- `APP_JOBS_SECRET` não tem mais default (`dev-secret-change-me` foi removido):
  sem ele o `/admin/jobs/inactivity` seria disparável por qualquer um em
  produção. Local, qualquer valor serve (ex.: `dev-secret`).
- Demais variáveis de `application.yml` têm default e são **opcionais** para rodar
  local: `JWT_EXPIRATION`, `REFRESH_TOKEN_EXPIRATION`, `MAX_FILE_SIZE_MB`,
  `RATE_LIMIT_REGISTER_PER_HOUR`/`LOGIN_PER_HOUR`/`FEEDBACK_PER_HOUR`/`FORGOT_PASSWORD_PER_HOUR`,
  `RESEND_API_KEY`, `APP_FRONTEND_URL`, `APP_CORS_ALLOWED_ORIGIN`,
  `APP_MAIL_FROM`, `SWAGGER_ENABLED` (default `false` — defina `true` local para ver o
  Swagger UI), `APP_TRUSTED_PROXY_HOPS` (default `1` — quantos
  proxies confiáveis da própria infra precedem o IP do cliente no
  `X-Forwarded-For`, ver [DEPLOYMENT.md](DEPLOYMENT.md)), `APP_AUTH_COOKIE_SECURE`
  (default `true` — atributo `Secure` do cookie httpOnly de refresh token;
  `application-local.yml` já sobrescreve para `false`, então não precisa mexer nela
  rodando local, mesmo fora do Docker), `PORT`.
- `HCAPTCHA_SECRET` tem default vazio, mas o bypass só é aceito com o profile `local`
  ativo (`CaptchaService` falha no startup fora dele). Local, deixe vazio
  para captcha desligado.

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
APP_JOBS_SECRET=<qualquer-string-para-dev> \
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
