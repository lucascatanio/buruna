# Deploy (GCP)

> Infraestrutura de produção. Para arquitetura de código, veja [ARCHITECTURE.md](ARCHITECTURE.md);
> para rodar localmente, [DEVELOPMENT.md](DEVELOPMENT.md).

## 1. Visão geral

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         CLIENTE (Browser)                                    │
│              React SPA — pdfjs-dist v4 (visualizador de PDF)                │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │ HTTPS — TLS automático
                                 │ domínio: buruna.com.br
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│            Cloud Run: buruna-frontend (us-east1)                             │
│            nginx (container Docker — imagem multi-stage)                     │
│                                                                              │
│  GET /*          → serve build estático do React (try_files + SPA)          │
│  POST /api/*     → proxy_pass → buruna-backend (run.app público)            │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │ HTTPS pela URL pública do run.app (sem VPC)
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│            Cloud Run: buruna-backend (us-east1)                              │
│            Spring Boot :8080 — Monolito Modular                              │
│   contextos: identity · manga · reading · engagement · admin                 │
│   RateLimitFilter → JwtFilter → Controllers → Use cases                     │
└──────────────────┬──────────────────────────────┬───────────────────────────┘
                   │ Direct VPC egress             │ HTTPS
                   ▼                               ▼
    ┌──────────────────────────┐   ┌────────────────────────────────────────────┐
    │  GCE e2-micro (us-east1-b│   │  GCS: buruna-files-catanio                │
    │  PostgreSQL 16 em Docker │   │  (southamerica-east1)                     │
    │  Tabelas via Flyway      │   │  volumes/{mangaId}/{uuid}.pdf (ADR-40)    │
    └──────────────────────────┘   │  /uuid-da-capa.jpg     (capa ofuscada)    │
                                   │  URLs assinadas V4 (geradas pelo backend): │
                                   │    leitura de PDF:    30 min              │
                                   │    capa privada:       1 h                │
                                   │    upload PUT:        15 min              │
                                   └──────────────────────┬─────────────────────┘
                                                          │ HTTPS direto
                                                          ▼
                                                 CLIENTE (browser)
                                                 PUT  → upload de PDF
                                                 GET  → leitura de PDF

┌──────────────────────────────────────────────────────────────────────────────┐
│  Cloud Scheduler (us-east1) — job "buruna-inactivity"                       │
│  Cron: "0 2 * * *" (02:00 UTC)                                              │
│  POST /api/admin/jobs/inactivity, header: X-Job-Secret: <APP_JOBS_SECRET>   │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                ▼
                    buruna-backend → RunInactivityUseCase (ADR-03)

┌──────────────────────────────────────────────────────────────────────────────┐
│  Pub/Sub (global) — tópico "password-reset-requests"                        │
│  Publicado por buruna-backend (PubSubPublisher, REST, dentro da requisição)  │
│  Subscription push "password-reset-push" — OIDC:                            │
│    pubsub-push-invoker@buruna.iam.gserviceaccount.com                       │
│  → POST https://buruna-backend-922749062176.us-east1.run.app                │
│         /api/internal/pubsub/password-reset                                 │
│    ack deadline: 30s · retenção de mensagem: 1h                             │
└──────────────────────────────────────────────────────────────────────────────┘
                    buruna-backend → ProcessPasswordResetRequestUseCase (ADR-42)

┌──────────────────────────────────────────────────────────────────────────────┐
│  GitHub Actions (push → main)                                                │
│  1. google-github-actions/auth (Workload Identity Federation)                │
│  2. docker build → docker push → Artifact Registry (us-east1)               │
│  3. gcloud run deploy (buruna-backend / buruna-frontend)                     │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│  Secret Manager (us-east1) — injeta env vars no Cloud Run no deploy:         │
│  DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, GCS_BUCKET_NAME,                 │
│  RESEND_API_KEY, APP_JOBS_SECRET, HCAPTCHA_SECRET,                          │
│  APP_PROXY_SECRET, APP_TRUSTED_PROXY_HOPS, APP_CORS_ALLOWED_ORIGIN,        │
│  APP_PUBSUB_PASSWORD_RESET_TOPIC, APP_PUBSUB_PUSH_AUDIENCE,                │
│  APP_PUBSUB_PUSH_SERVICE_ACCOUNT, …                                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 2. Resumo dos serviços

| Serviço             | Produto GCP                          | Região             | Observação                          |
|---------------------|---------------------------------------|--------------------|-------------------------------------|
| Backend API         | Cloud Run                            | us-east1           | Stateless, escala para zero         |
| Frontend SPA        | Cloud Run                            | us-east1           | nginx + build estático React        |
| Banco de dados      | GCE e2-micro + Docker (PostgreSQL 16)| us-east1-b         | Free tier permanente                |
| Arquivos PDF/capas  | GCS `buruna-files-catanio`           | southamerica-east1 | Latência baixa para usuários BR     |
| Jobs agendados      | Cloud Scheduler                      | us-east1           | Job `buruna-inactivity`: trigger diário de `RunInactivityUseCase` (ADR-03) |
| Mensageria          | Pub/Sub                              | global             | Tópico `password-reset-requests` + push subscription `password-reset-push` (ADR-42) |
| Imagens Docker      | Artifact Registry                    | us-east1           | Pipeline de CI/deploy               |
| Secrets             | Secret Manager                       | us-east1           | Injetados no Cloud Run              |
| CI/CD               | GitHub Actions                       | —                  | Deploy automático no push para main |
| E-mail              | Resend API                           | —                  | Domínio @buruna.com.br, DKIM/SPF/DMARC |
| Monitoramento       | UptimeRobot                          | —                  | Alerta de downtime por e-mail       |
| Domínio             | buruna.com.br (registro.br)          | —                  | TLS automático via Cloud Run        |
| Documentação API    | SpringDoc OpenAPI 2.8                 | —                  | Swagger UI em /api/swagger-ui.html  |

Decisões e tradeoffs por trás de cada escolha de infra: Cloud Run separado por serviço
([ADR-22](adr/ADR-22-cloud-run-separado-frontend-backend.md)), PostgreSQL em GCE
([ADR-23](adr/ADR-23-postgresql-gce-e2-micro-docker.md)), bucket em
southamerica-east1 x Cloud Run em us-east1 ([ADR-26](adr/ADR-26-gcs-southamerica-cloudrun-useast1.md)),
UptimeRobot ([ADR-08](adr/ADR-08-uptimerobot-monitoramento.md)), nginx como reverse
proxy ([ADR-04](adr/ADR-04-nginx-reverse-proxy-frontend.md)), `@Async`/`@Scheduled` internos
e a migração do job de inatividade para o Cloud Scheduler
([ADR-03](adr/ADR-03-async-e-scheduled-internos.md)), forgot password via Pub/Sub push
([ADR-42](adr/ADR-42-forgot-password-via-pubsub.md)).

## 3. URLs assinadas do GCS (V4)

| Tipo                    | Método HTTP | Expiração | Quem usa                       |
|-------------------------|-------------|-----------|---------------------------------|
| Leitura de PDF          | GET         | 30 min    | Leitor no browser               |
| Leitura de capa privada | GET         | 1 hora    | Tela de coleção privada         |
| Upload de volume        | PUT         | 15 min    | Frontend (PUT direto ao GCS)    |

- URLs geradas pelo backend com credenciais de service account; o browser acessa o GCS
  diretamente, sem passar pelo backend.
- Após expiração, o GCS retorna `403 Forbidden`.
- O header `Authorization` **não** deve ser enviado ao GCS — quebraria a assinatura.
- Decisão e tradeoffs: [ADR-07](adr/ADR-07-gcs-urls-assinadas-v4.md) (bucket privado +
  URLs assinadas em vez de bucket público).

## 4. CORS

**Backend** (Spring Security, `SecurityConfig`):

| Origem permitida                                                | Métodos                                |
|------------------------------------------------------------------|-----------------------------------------|
| `http://localhost:5173` / `http://localhost:3000` (dev)          | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| Valor de `APP_CORS_ALLOWED_ORIGIN` (produção: URL do Cloud Run)  | GET, POST, PUT, PATCH, DELETE, OPTIONS |

`allowCredentials: true` — necessário para Authorization header.

**GCS Bucket** (`gcs-cors.json`):

| Origem permitida                                          | Métodos        | Max-Age |
|-------------------------------------------------------------|-----------------|---------|
| `https://buruna.com.br`                                    | GET, PUT, HEAD | 3600s   |
| `https://buruna-frontend-922749062176.us-east1.run.app`    | GET, PUT, HEAD | 3600s   |
| `http://localhost`, `http://192.168.100.192` (teste local contra o bucket de prod) | GET, PUT, HEAD | 3600s   |

PUT necessário para upload direto; GET/HEAD para leitura de PDF pelo browser.
`responseHeader` inclui `Range`/`Content-Range` (leitura parcial do PDF) e `x-goog-content-length-range`, que a Signed URL de upload
assina para limitar o tamanho do PUT ([ADR-40](adr/ADR-40-objectname-vinculado-ao-manga.md)).

**GCS Bucket — lifecycle** (`gcs-lifecycle.json`): apaga objetos em `pending/` com
mais de 1 dia — uploads que nunca chegaram ao finalize ([ADR-40](adr/ADR-40-objectname-vinculado-ao-manga.md)).
O GCS aplica a regra de forma assíncrona, então a remoção pode levar até mais um dia.

Aplicar ou atualizar as duas configurações (o bucket de dev usa `gcs-cors-dev.json`):

```bash
# o --lifecycle-file SUBSTITUI as regras existentes — confira antes se já há alguma
gcloud storage buckets describe gs://<bucket> --format="default(cors_config,lifecycle_config)"

gcloud storage buckets update gs://<bucket> --cors-file=gcs-cors.json
gcloud storage buckets update gs://<bucket> --lifecycle-file=gcs-lifecycle.json
```

## 5. Deploy manual (fallback)

```bash
# Backend
cd backend
docker build -t <artifact-registry-url>/buruna-backend:latest .
docker push <artifact-registry-url>/buruna-backend:latest
gcloud run deploy buruna-backend --image <artifact-registry-url>/buruna-backend:latest --region us-east1

# Frontend
cd frontend
docker build -t <artifact-registry-url>/buruna-frontend:latest .
docker push <artifact-registry-url>/buruna-frontend:latest
gcloud run deploy buruna-frontend --image <artifact-registry-url>/buruna-frontend:latest --region us-east1
```

Em uso normal, o deploy é automático via GitHub Actions no push para `main` — o fluxo
acima é só para reproduzir manualmente em caso de incidente com o pipeline.

## 6. Versão e tag

Todo deploy na `main` sobe a versão. Ela fica em `frontend/package.json` e é a que o
frontend exibe (`v{__APP_VERSION__}` no `AuthLayout`, injetada pelo `vite.config.ts`); a
tag git `vX.Y.Z` marca o commit que foi para produção. O `backend/pom.xml` não é
versionado (`0.0.1-SNAPSHOT`).

Os PRs `dev` → `main` (release) e `main` → `dev` (sincronização) entram com **merge
commit**, nunca squash nem rebase: squash criaria na `main` um commit sem a história da `dev`
como ancestral, e as duas divergiriam para sempre. PRs de feature para `dev` usam squash
(ver [CONTRIBUTING.md](../CONTRIBUTING.md)).

1. No PR `dev` → `main`, escolha o número pelo semver a partir da última tag
   (`git tag --sort=-v:refname | head -1`): só correções → patch, feature nova → minor,
   quebra de contrato da API → major.
2. Suba a versão num commit do próprio PR, `chore: bump versão para X.Y.Z`:
   ```bash
   cd frontend && npm version X.Y.Z --no-git-tag-version   # atualiza package.json e package-lock.json
   ```
3. Depois do merge na `main` e do deploy verde (`/api/health` respondendo), crie a tag no
   commit de merge e publique:
   ```bash
   git fetch origin && git tag -a vX.Y.Z origin/main -m "vX.Y.Z" && git push origin vX.Y.Z
   ```
4. Sincronize a `main` de volta na `dev` com um PR `main` → `dev`
   (`gh pr create --base dev --head main`). O merge da release cria na `main` um commit que
   a `dev` não tem; sem esse PR as duas nunca se reencontram e o GitHub mostra a `dev`
   "atrás" da `main`. O auto-delete de branches do repositório não apaga `main` nem `dev`,
   que são protegidas.

## 7. Pré-requisitos de infraestrutura

- Bucket GCS criado, com `gcs-credentials.json` de uma Service Account com
  `roles/storage.objectAdmin`.
- Domínio próprio configurado (DKIM/SPF/DMARC) se for usar Resend para e-mail com
  domínio customizado — ver [ADR-29](adr/ADR-29-resend-api-email-dominio-proprio.md).
- `APP_JOBS_SECRET` é **obrigatório** em produção (o backend não sobe sem ele).
  Sem essa variável, o job de inatividade (`/admin/jobs/inactivity`,
  `permitAll`) ficaria disparável por qualquer um.
- `HCAPTCHA_SECRET` também é **obrigatório** fora do profile `local`:
  sem ele o registro ficaria sem captcha algum.
- `APP_TRUSTED_PROXY_HOPS` define quantos proxies confiáveis da própria infra (o
  nginx do `buruna-frontend`, e qualquer outro salto que o Cloud Run acrescente ao
  `X-Forwarded-For`) precedem o IP real do cliente. O `ClientIpResolver`
  (`shared/security`) usa esse valor para o rate limit de login/registro/forgot não
  ser burlável forjando o header. Chamada direta ao `run.app` do backend não burla
  mais esse cálculo: o `ProxySecretFilter` (`APP_PROXY_SECRET`, abaixo) a recusa com 403
  antes do rate limit. Para calibrar o valor:
  1. Ligue o log do header: variável `LOGGING_LEVEL_COM_BURUNA_SHARED_SECURITY=DEBUG`
     no `buruna-backend` (nível de pacote: o Spring converte a variável para
     minúsculas, então o nome da classe não funcionaria).
  2. Descubra seu IP público (`curl -s https://ifconfig.me`) e faça uma tentativa de
     login passando pelo frontend, com um header forjado:
     `curl -X POST https://<frontend>/api/auth/login -H 'Content-Type: application/json' -H 'X-Forwarded-For: 6.6.6.6' -d '{"email":"calibracao@example.com","password":"x"}'`.
  3. Nos logs do backend, procure `X-Forwarded-For=`. O `6.6.6.6` forjado fica à
     esquerda; ache o seu IP real na lista. Com as entradas numeradas a partir de 0,
     `APP_TRUSTED_PROXY_HOPS = total de entradas − posição do seu IP`
     (ex.: `6.6.6.6, <seu IP>, <proxy>, <proxy>` → 4 − 1 = 3).
  4. Defina `APP_TRUSTED_PROXY_HOPS` e remova a variável de log: o log registra IPs
     de usuários e só deve ficar ligado durante a calibração.
- `APP_PROXY_SECRET` (backend) e `BACKEND_PROXY_SECRET` (frontend) são o **mesmo**
  segredo, vindo do Secret Manager (`buruna-proxy-secret`). O nginx do `buruna-frontend`
  envia `X-Proxy-Secret` em todo `/api/`; o `ProxySecretFilter` do backend responde 403 a
  qualquer requisição sem o header correto, o que impede chamar o `run.app` do backend
  direto para forjar o `X-Forwarded-For` e burlar o rate limit
  ([ADR-43](adr/ADR-43-segredo-compartilhado-nginx-backend.md)). Sem `APP_PROXY_SECRET`
  o filtro fica desligado (dev local/testes) e o backend loga um WARN no startup. Sem
  `BACKEND_PROXY_SECRET`, o nginx sobe normalmente e manda o header vazio, então a imagem
  nova pode ir para produção antes de o segredo existir.
  Ficam isentos, por terem autenticação própria ou não passarem pelo nginx:
  `/internal/pubsub/**` (OIDC do Pub/Sub), `/admin/jobs/**` (`X-Job-Secret` do Cloud
  Scheduler) e `/health` (probe do Cloud Run; só devolve `{"status":"UP"}`).

  Criar o segredo e liberar as service accounts (substitua `<SA_BACKEND>` e `<SA_FRONTEND>`
  pelas contas de execução dos dois serviços):

  ```bash
  openssl rand -hex 32 | tr -d '\n' | gcloud secrets create buruna-proxy-secret --data-file=-
  gcloud secrets add-iam-policy-binding buruna-proxy-secret \
    --member="serviceAccount:<SA_BACKEND>" --role="roles/secretmanager.secretAccessor"
  gcloud secrets add-iam-policy-binding buruna-proxy-secret \
    --member="serviceAccount:<SA_FRONTEND>" --role="roles/secretmanager.secretAccessor"
  ```

  **Ordem de rollout** (inverter derruba o site, porque o nginx ainda não enviaria o
  header e o backend passaria a exigi-lo):

  1. Frontend primeiro. O backend ainda não tem a propriedade, então o filtro segue
     desligado e o header extra é ignorado:
     ```bash
     gcloud run services update buruna-frontend --region us-east1 \
       --update-secrets=BACKEND_PROXY_SECRET=buruna-proxy-secret:latest
     ```
  2. Confira que o site continua funcionando (login, biblioteca).
  3. Backend depois, ligando o filtro:
     ```bash
     gcloud run services update buruna-backend --region us-east1 \
       --update-secrets=APP_PROXY_SECRET=buruna-proxy-secret:latest
     ```
  4. Valide: `curl -i https://<backend>.run.app/api/auth/password/reset-info?token=x`
     responde 403 e o mesmo caminho pelo frontend responde 200.

  Rotação: crie uma versão nova do segredo e rode os dois `update-secrets` em seguida
  (há uma janela curta de 403 entre eles; o `:latest` só é lido ao subir a revisão).
  O `deploy.yml` não precisa mudar: `gcloud run deploy` preserva os segredos já ligados
  ao serviço.
- `SWAGGER_ENABLED` tem default `false`; defina `true` explicitamente se quiser
  expor `/api/swagger-ui.html` em algum ambiente.
- `APP_PUBSUB_PASSWORD_RESET_TOPIC`, `APP_PUBSUB_PUSH_AUDIENCE` e
  `APP_PUBSUB_PUSH_SERVICE_ACCOUNT` são **obrigatórios** fora do profile `local`
  (`PubSubPasswordResetRequests` e `PubSubPushAuthenticator` falham no startup sem eles) —
  ver [ADR-42](adr/ADR-42-forgot-password-via-pubsub.md):
  - `APP_PUBSUB_PASSWORD_RESET_TOPIC`: o tópico no formato
    `projects/<projeto>/topics/password-reset-requests`, para onde `POST /auth/password/forgot`
    publica.
  - Subscription push `password-reset-push`, associada a esse tópico: entrega para
    `https://buruna-backend-922749062176.us-east1.run.app/api/internal/pubsub/password-reset`,
    autenticação OIDC com a conta de serviço
    `pubsub-push-invoker@buruna.iam.gserviceaccount.com`, ack deadline de 30 s e retenção
    de mensagem de 1 h (consistente com a expiração do token de reset).
  - `APP_PUBSUB_PUSH_AUDIENCE`: a audience esperada no token OIDC (normalmente a própria
    URL do endpoint de push). `APP_PUBSUB_PUSH_SERVICE_ACCOUNT`: o e-mail da conta de
    serviço acima — `PubSubPushAuthenticator` recusa qualquer token assinado por outra
    conta.
- Job do Cloud Scheduler `buruna-inactivity`: cron `0 2 * * *` (02:00 UTC),
  `POST /api/admin/jobs/inactivity`, header `X-Job-Secret: <APP_JOBS_SECRET>`. Substituiu o
  `@Scheduled` interno de `RunInactivityUseCase` — ver atualização de 2026-09-24 em
  [ADR-03](adr/ADR-03-async-e-scheduled-internos.md).

- Job do Cloud Scheduler `buruna-storage-orphans` (a criar, semanal, ex.: cron `0 3 * * 0`):
  `POST /api/admin/jobs/storage-orphans`, header `X-Job-Secret: <APP_JOBS_SECRET>`. Apaga de
  `volumes/` no GCS os arquivos sem linha em `volumes` e com mais de 7 dias
  (`DeleteOrphanVolumeFilesUseCase`). Enquanto o job não existir no Scheduler, os órfãos
  continuam se acumulando. Antes de criar o job, rode uma vez à mão com `?dryRun=true`
  (só conta, não apaga) e confira se `orphans` é plausível. Trava de segurança: se os órfãos
  passarem de 5 e de 10% do analisado, o job não apaga nada e loga ERROR, porque essa
  proporção indica descasamento entre o nome no bucket e o `file_url` do banco.

Não são necessários para rodar local — o profile `local` usa `LocalStorageClient`
(filesystem) em vez do GCS real. Ver [DEVELOPMENT.md](DEVELOPMENT.md).
