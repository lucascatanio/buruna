# Burūna

Biblioteca pessoal de mangás com autenticação, upload e leitor web inline.

**[buruna.com.br](https://buruna.com.br)**

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Spring Security, JWT + Refresh Token |
| Banco de dados | PostgreSQL 16, Flyway (V1–V16) |
| Storage | Google Cloud Storage — URLs assinadas V4 |
| E-mail | Gmail SMTP via Spring Mail |
| Frontend | React 18, TypeScript, Vite, shadcn/ui, Tailwind CSS v4 |
| Proxy | nginx (no container do frontend) |
| Infra | Docker, GCP (Cloud Run + GCE + GCS + Secret Manager) |

---

## Arquitetura em produção

```
Browser
  │ HTTPS (buruna.com.br)
  ▼
Cloud Run: buruna-frontend   (nginx + React SPA — us-east1)
  │ proxy /api/*
  ▼
Cloud Run: buruna-backend    (Spring Boot :8080 — us-east1)
  │                  │
  ▼                  ▼
GCE e2-micro       GCS bucket (southamerica-east1)
PostgreSQL 16      PDFs e capas com nomes ofuscados (UUID)
(via VPC)          URLs assinadas: leitura 30min, upload PUT 15min
```

PDFs são servidos diretamente do GCS ao browser via URL assinada, sem passar pelo backend.

Para mais detalhes: [`docs/buruna_architecture.md`](docs/buruna_architecture.md)

---

## Setup local

### Pré-requisitos

- Docker e Docker Compose
- Bucket no Google Cloud Storage criado
- `gcs-credentials.json` — Service Account com `roles/storage.objectAdmin`
- Gmail com [App Password](https://myaccount.google.com/apppasswords) configurado

### 1. Clone

```bash
git clone https://github.com/seu-usuario/buruna.git
cd buruna
```

### 2. Variáveis de ambiente

```bash
cp .env.example .env
# edite o .env com seus valores
```

### 3. Credenciais do GCS

Coloque o arquivo na raiz do projeto:

```
buruna/
└── gcs-credentials.json (.gitignore)
```

### 4. Suba os containers

```bash
docker compose up --build
```

Disponível em: **http://localhost**

---

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| `DB_URL` | JDBC URL do PostgreSQL |
| `DB_USER` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco |
| `JWT_SECRET` | Chave para assinar tokens JWT |
| `JWT_EXPIRATION` | Expiração do access token em segundos (padrão: 3600) |
| `REFRESH_TOKEN_EXPIRATION` | Expiração do refresh token em segundos (padrão: 604800) |
| `GCS_BUCKET_NAME` | Nome do bucket GCS |
| `GCS_CREDENTIALS_PATH` | Caminho para o `gcs-credentials.json` |
| `MAIL_USERNAME` | E-mail para envio (Gmail) |
| `MAIL_PASSWORD` | App Password do Gmail |
| `ADMIN_EMAIL` | E-mail que recebe notificações de cadastro pendente |
| `APP_CORS_ALLOWED_ORIGIN` | Origem permitida no CORS (ex: `https://buruna.com.br`) |
| `APP_MAIL_FROM` | Remetente dos e-mails (ex: `noreply@buruna.com.br`) |
| `APP_JOBS_SECRET` | Segredo para o endpoint de trigger do InactivityJob |
| `MAX_FILE_SIZE_MB` | Tamanho máximo de arquivo em MB (padrão: 500) |
| `RATE_LIMIT_REGISTER_PER_HOUR` | Limite de cadastros por IP por hora (padrão: 5) |
| `RATE_LIMIT_LOGIN_PER_HOUR` | Limite de logins por IP por hora (padrão: 10) |
| `PORT` | Porta do backend — injetada automaticamente pelo Cloud Run (padrão: 8080) |

---

## Estrutura do repositório

```
buruna/
├── backend/
│   ├── src/main/java/com/buruna/
│   │   ├── auth/          autenticação, JWT, refresh token
│   │   ├── user/          usuários, roles, inatividade
│   │   ├── manga/         mangás, volumes, tags, coleção privada
│   │   ├── reader/        leitor, progresso, histórico
│   │   ├── admin/         dashboard, job controller
│   │   ├── notification/  envio de e-mails (@Async)
│   │   └── infra/         config, storage, security, exceptions
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── db/migration/  V1..V16
│   └── Dockerfile         multi-stage (Maven build + JRE runtime)
├── frontend/
│   ├── src/
│   │   ├── pages/
│   │   ├── components/
│   │   ├── store/         Zustand (auth)
│   │   └── lib/           axios, utils
│   ├── public/
│   │   └── cmaps/         mapas de caracteres para pdfjs (japonês/coreano/chinês)
│   ├── nginx.conf         proxy /api/* + SPA fallback
│   └── Dockerfile         multi-stage (Node build + nginx runtime)
├── docs/
│   ├── buruna_architecture.md   referência técnica completa
│   └── buruna_roadmap_mvp.md    progresso e backlog
├── scripts/
│   └── test-phase8.sh     smoke tests do painel admin
├── gcs-cors.json          configuração de CORS do bucket GCS
├── docker-compose.yml     ambiente local
├── .env.example
└── README.md
```

---

## Deploy em produção (GCP)

O deploy usa Cloud Run para backend e frontend separados. Resumo dos comandos:

```bash
PROJECT=buruna
REPO=us-east1-docker.pkg.dev/$PROJECT/buruna

# Build e push
docker build -t $REPO/backend:latest ./backend && docker push $REPO/backend:latest
docker build -t $REPO/frontend:latest ./frontend && docker push $REPO/frontend:latest

# Deploy backend
gcloud run deploy buruna-backend \
  --image=$REPO/backend:latest \
  --region=us-east1 \
  --vpc-connector=buruna-connector \
  --service-account=buruna-backend@buruna.iam.gserviceaccount.com

# Deploy frontend
gcloud run deploy buruna-frontend \
  --image=$REPO/frontend:latest \
  --region=us-east1 \
  --allow-unauthenticated
```

Todas as variáveis sensíveis estão no Secret Manager — não precisam ser passadas no comando acima após o primeiro deploy.

Para o passo a passo completo de setup da infra GCP (VPC, GCE, Scheduler, domínio): [`docs/buruna_architecture.md`](docs/buruna_architecture.md)

---

## Módulos do backend

Cada módulo segue a mesma estrutura:

```
modulo/
├── controller/    entrada HTTP — valida request, delega ao service
├── service/       casos de uso — orquestra regras de negócio
├── domain/        entidades JPA e enums
├── repository/    interfaces Spring Data JPA
├── dto/           records imutáveis (Request / Response)
└── exception/     exceções de domínio do módulo
```

---

## Comandos úteis (local)

```bash
# subir apenas o banco
docker compose up postgres

# logs em tempo real
docker compose logs -f backend

# rebuild de um serviço
docker compose up --build backend

# derrubar tudo e limpar volumes
docker compose down -v

# rodar testes do backend
cd backend && ./mvnw test
```