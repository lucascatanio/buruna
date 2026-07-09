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
│  POST /api/*     → proxy_pass → buruna-backend (VPC connector)              │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │ HTTP interno via VPC connector
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│            Cloud Run: buruna-backend (us-east1)                              │
│            Spring Boot :8080 — Monolito Modular                              │
│   contextos: identity · manga · reading · engagement · admin                 │
│   RateLimitFilter → JwtFilter → Controllers → Use cases                     │
└──────────────────┬──────────────────────────────┬───────────────────────────┘
                   │ VPC connector                 │ HTTPS
                   ▼                               ▼
    ┌──────────────────────────┐   ┌────────────────────────────────────────────┐
    │  GCE e2-micro (us-east1-b│   │  GCS: buruna-files-catanio                │
    │  PostgreSQL 16 em Docker │   │  (southamerica-east1)                     │
    │  Tabelas via Flyway      │   │  /uuid-do-volume.pdf   (PDF ofuscado)     │
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
│  Cloud Scheduler (us-east1) — Cron: "0 0 2 * * *" (diário às 02:00)          │
│  POST /admin/jobs/inactivity, header: X-Job-Secret: <APP_JOBS_SECRET>       │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                ▼
                    buruna-backend → RunInactivityUseCase

┌──────────────────────────────────────────────────────────────────────────────┐
│  GitHub Actions (push → main)                                                │
│  1. google-github-actions/auth (Workload Identity Federation)                │
│  2. docker build → docker push → Artifact Registry (us-east1)               │
│  3. gcloud run deploy (buruna-backend / buruna-frontend)                     │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│  Secret Manager (us-east1) — injeta env vars no Cloud Run no deploy:         │
│  DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, GCS_BUCKET_NAME,                 │
│  RESEND_API_KEY, APP_JOBS_SECRET, APP_CORS_ALLOWED_ORIGIN, …                │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 2. Resumo dos serviços

| Serviço             | Produto GCP                          | Região             | Observação                          |
|---------------------|---------------------------------------|--------------------|-------------------------------------|
| Backend API         | Cloud Run                            | us-east1           | Stateless, escala para zero         |
| Frontend SPA        | Cloud Run                            | us-east1           | nginx + build estático React        |
| Banco de dados      | GCE e2-micro + Docker (PostgreSQL 16)| us-east1-b         | Free tier permanente                |
| Arquivos PDF/capas  | GCS `buruna-files-catanio`           | southamerica-east1 | Latência baixa para usuários BR     |
| Jobs agendados      | Cloud Scheduler                      | us-east1           | Trigger diário de `RunInactivityUseCase` |
| Imagens Docker      | Artifact Registry                    | us-east1           | Pipeline de CI/deploy               |
| Secrets             | Secret Manager                       | us-east1           | Injetados no Cloud Run              |
| CI/CD               | GitHub Actions                       | —                  | Deploy automático no push para main |
| E-mail              | Resend API                           | —                  | Domínio @buruna.com.br, DKIM/SPF/DMARC |
| Monitoramento       | UptimeRobot                          | —                  | Alerta de downtime por e-mail       |
| Domínio             | buruna.com.br (registro.br)          | —                  | TLS automático via Cloud Run        |
| Documentação API    | SpringDoc OpenAPI 2.7                 | —                  | Swagger UI em /api/swagger-ui.html  |

Decisões e tradeoffs por trás de cada escolha de infra: Cloud Run separado por serviço
([ADR-22](adr/ADR-22-cloud-run-separado-frontend-backend.md)), PostgreSQL em GCE
([ADR-23](adr/ADR-23-postgresql-gce-e2-micro-docker.md)), bucket em
southamerica-east1 x Cloud Run em us-east1 ([ADR-26](adr/ADR-26-gcs-southamerica-cloudrun-useast1.md)),
UptimeRobot ([ADR-08](adr/ADR-08-uptimerobot-monitoramento.md)), nginx como reverse
proxy ([ADR-04](adr/ADR-04-nginx-reverse-proxy-frontend.md)).

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

PUT necessário para upload direto; GET/HEAD para leitura de PDF pelo browser.

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

## 6. Pré-requisitos de infraestrutura

- Bucket GCS criado, com `gcs-credentials.json` de uma Service Account com
  `roles/storage.objectAdmin`.
- Domínio próprio configurado (DKIM/SPF/DMARC) se for usar Resend para e-mail com
  domínio customizado — ver [ADR-29](adr/ADR-29-resend-api-email-dominio-proprio.md).

Não são necessários para rodar local — o profile `local` usa `LocalStorageClient`
(filesystem) em vez do GCS real. Ver [DEVELOPMENT.md](DEVELOPMENT.md).
