# Burūna — Arquitetura e Fluxogramas

> Documento de referência para quem vai trabalhar no projeto sem contexto prévio.
> Cobre a infraestrutura GCP, os fluxos de usuário, o modelo de dados,
> segurança e as decisões de arquitetura que foram tomadas até aqui.

---

## 1. Visão geral da infraestrutura

### 1.1 Diagrama completo

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
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │ GET /*          → serve build estático do React (try_files + SPA)    │   │
│  │ POST /api/*     → proxy_pass → buruna-backend (VPC connector)        │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │ HTTP interno via VPC connector
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│            Cloud Run: buruna-backend (us-east1)                              │
│            Spring Boot :8080 — Monolito Modular                              │
│                                                                              │
│   ┌────────┐  ┌──────┐  ┌────────┐  ┌────────┐  ┌──────┐  ┌────────────┐   │
│   │  auth  │  │ user │  │ manga  │  │ reader │  │admin │  │notification│   │
│   └────────┘  └──────┘  └────────┘  └────────┘  └──────┘  └────────────┘   │
│                                                                              │
│   RateLimitFilter → JwtFilter → Controllers → Services                      │
└──────────────────┬──────────────────────────────┬───────────────────────────┘
                   │ VPC connector                 │ HTTPS
                   ▼                               ▼
    ┌──────────────────────────┐   ┌────────────────────────────────────────────┐
    │  GCE e2-micro (us-east1-b│   │  GCS: buruna-files-catanio                │
    │  PostgreSQL 16 em Docker │   │  (southamerica-east1)                     │
    │                          │   │                                            │
    │  Tabelas gerenciadas por │   │  /uuid-do-volume.pdf   (PDF ofuscado)     │
    │  Flyway (V1..V16)        │   │  /uuid-da-capa.jpg     (capa ofuscada)    │
    └──────────────────────────┘   │                                            │
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
│  Cloud Scheduler (us-east1)                                                  │
│  Cron: "0 0 2 * * *" (diário às 02:00)                                      │
│                                                                              │
│  POST /api/admin/jobs/inactivity                                             │
│  header: X-Job-Secret: <APP_JOBS_SECRET>                                     │
│                               │                                              │
└───────────────────────────────┼──────────────────────────────────────────────┘
                                ▼
                    buruna-backend → InactivityJob
                    (verifica last_access_at de todos os usuários ACTIVE)

┌──────────────────────────────────────────────────────────────────────────────┐
│  GitHub Actions (push → main)                                                │
│  jobs: deploy-backend / deploy-frontend                                      │
│                                                                              │
│  1. google-github-actions/auth (Workload Identity Federation)                │
│  2. docker build → docker push                                               │
│  3. gcloud run deploy                                                        │
│                ↓                                                             │
│  Artifact Registry (us-east1)                                                │
│  buruna-backend:latest                                                       │
│  buruna-frontend:latest                                                      │
│                ↓                                                             │
│  Cloud Run services                                                          │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│  Secret Manager (us-east1)                                                   │
│  Injeta variáveis de ambiente no Cloud Run no momento do deploy:             │
│  DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, GCS_BUCKET_NAME,                 │
│  MAIL_USERNAME, MAIL_PASSWORD, APP_JOBS_SECRET, APP_CORS_ALLOWED_ORIGIN, …  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Resumo dos serviços

| Serviço             | Produto GCP                          | Região             | Observação                          |
|---------------------|--------------------------------------|--------------------|-------------------------------------|
| Backend API         | Cloud Run                            | us-east1           | Stateless, escala para zero         |
| Frontend SPA        | Cloud Run                            | us-east1           | nginx + build estático React        |
| Banco de dados      | GCE e2-micro + Docker (PostgreSQL 16)| us-east1-b         | Free tier permanente                |
| Arquivos PDF/capas  | GCS `buruna-files-catanio`           | southamerica-east1 | Latência baixa para usuários BR     |
| Jobs agendados      | Cloud Scheduler                      | us-east1           | Trigger diário do InactivityJob     |
| Imagens Docker      | Artifact Registry                    | us-east1           | Pipeline de CI/deploy               |
| Secrets             | Secret Manager                       | us-east1           | Injetados no Cloud Run              |
| CI/CD               | GitHub Actions                       | —                  | Deploy automático no push para main |
| Monitoramento       | UptimeRobot                          | —                  | Alerta de downtime por e-mail       |
| Domínio             | buruna.com.br (registro.br)          | —                  | TLS automático via Cloud Run        |
| Documentação API    | SpringDoc OpenAPI 2.7                | —                  | Swagger UI em /api/swagger-ui.html  |

---

## 2. Fluxos de usuário completos

### 2.1 Cadastro e aprovação

```
VISITANTE                         BROWSER                    BACKEND                   ADMIN
    │                                │                           │                        │
    │── preenche formulário ─────────►│                           │                        │
    │   (email, username, senha,      │                           │                        │
    │    foto, mensagem de apres.)    │                           │                        │
    │                                │── POST /auth/register ────►│                        │
    │                                │                           │ valida campos           │
    │                                │                           │ verifica rate limit     │
    │                                │                           │ (5 req/h por IP)        │
    │                                │                           │ salva User{PENDING}     │
    │                                │                           │ BCrypt(senha)           │
    │                                │                           │──── e-mail ────────────►│
    │                                │◄── 201 Created ───────────│  (@Async)               │
    │                                │                           │                        │
    │                                │                           │              Admin acessa painel
    │                                │                           │◄── GET /admin/users/pending ──│
    │                                │                           │─── lista pendentes ────────────►│
    │                                │                           │                        │
    │                                │                           │◄── POST /admin/users/{id}/approve │
    │                                │                           │ atualiza status→ACTIVE  │
    │                                │                           │──── e-mail ────────────►│ usuário
    │                                │                           │  (@Async)               │
    │◄── e-mail: "cadastro aprovado" ─────────────────────────────────────────────────────┘
    │
    └── pode fazer login agora
```

### 2.2 Autenticação

**Login:**
```
1. Frontend: POST /auth/login { email, password }
2. Backend: busca User por email, BCrypt.matches(password, hash)
3. Backend: verifica User.status == ACTIVE (rejeita PENDING/INACTIVE)
4. Backend: gera accessToken (JWT, expira em 1h por padrão)
5. Backend: gera refreshToken (UUID, persiste em refresh_tokens com expires_at)
6. Backend: atualiza User.last_access_at
7. Backend: retorna { accessToken, refreshToken, expiresIn }
8. Frontend: armazena tokens e usa Bearer no header de requisições
```

**Refresh automático:**
```
1. Interceptor Axios detecta 401 em qualquer requisição
2. Frontend: POST /auth/refresh { refreshToken }
3. Backend: busca refresh_token no banco, valida expires_at
4. Backend: invalida token antigo (deleta do banco)
5. Backend: gera novo accessToken
6. Backend: retorna { accessToken, expiresIn }
7. Frontend: repete a requisição original com o novo token
```

**Logout:**
```
1. Frontend: POST /auth/logout { refreshToken }
2. Backend: deleta o refresh_token do banco
3. Frontend: limpa tokens armazenados, redireciona para /login
```

### 2.3 Leitura de mangá

```
BROWSER                                    BACKEND                     GCS
   │                                           │                         │
   │── GET /mangas?page=0&size=20 ────────────►│                         │
   │◄── lista de mangás (capa, título) ────────│                         │
   │                                           │                         │
   │── GET /mangas/{slug} ─────────────────────►│                         │
   │◄── detalhes + lista de volumes ───────────│                         │
   │                                           │                         │
   │── GET /reader/{volumeId}/url ─────────────►│                         │
   │                                           │── generateSignedUrl ────►│
   │                                           │◄── signed URL (30min) ──│
   │                                           │ incrementa view_count   │
   │                                           │ insere ReadingHistory   │
   │◄── { url: "https://storage.googleapis..." │                         │
   │                                           │                         │
   │── GET <signed URL> ──────────────────────────────────────────────────►│
   │◄── PDF binário ─────────────────────────────────────────────────────│
   │                                           │                         │
   │  (pdfjs renderiza página a página)        │                         │
   │                                           │                         │
   │── POST /reader/{volumeId}/progress ───────►│                         │
   │   { currentPage: 47 }                     │ upsert ReadingProgress  │
   │◄── 200 OK ─────────────────────────────────│                         │
```

> Se a signed URL expirar durante a leitura (depois de 30 min), o browser recebe 403 do GCS.
> O frontend detecta o erro e pede nova URL via `GET /reader/{volumeId}/url`.

### 2.4 Upload de volume público (duas fases)

```
BROWSER (COLLABORATOR+)              BACKEND                          GCS
   │                                    │                               │
   │── POST /mangas/{id}/volumes/ ──────►│                               │
   │   upload-url                        │ valida role (COLLAB+)         │
   │   { volumeNumber: 3 }              │ verifica nº duplicado         │
   │                                    │ gera objectName = uuid.pdf    │
   │                                    │── generateUploadSignedUrl ────►│
   │                                    │◄── PUT URL (15min) ───────────│
   │◄── { uploadUrl, objectName } ──────│                               │
   │                                    │                               │
   │── PUT <uploadUrl> ─────────────────────────────────────────────────►│
   │   Content-Type: application/pdf    │                               │ armazena
   │   [sem Authorization header]       │                               │ uuid.pdf
   │◄── 200 OK ──────────────────────────────────────────────────────────│
   │                                    │                               │
   │── POST /mangas/{id}/volumes/ ──────►│                               │
   │   finalize                          │── getBlob(objectName) ────────►│
   │   { objectName, volumeNumber }      │◄── metadados (md5, size) ────│
   │                                    │   (sem download do arquivo)   │
   │                                    │ verifica md5 duplicado        │
   │                                    │ persiste Volume no banco      │
   │◄── 201 Created { volume } ─────────│                               │
```

### 2.5 Upload privado (criar mangá + volume)

```
BROWSER (qualquer usuário ACTIVE)    BACKEND                          GCS
   │                                    │                               │
   │  Passo 1: criar o mangá            │                               │
   │── POST /my/mangas ─────────────────►│                               │
   │   { title, synopsis, ... }          │ valida cota disponível        │
   │                                    │ gera slug único               │
   │◄── 201 Created { id, slug } ───────│                               │
   │                                    │                               │
   │  Passo 2: upload do volume         │                               │
   │── POST /my/mangas/{id}/volumes/ ───►│                               │
   │   upload-url                        │ valida cota (bytes restantes) │
   │   { volumeNumber: 1 }              │ gera objectName = uuid.pdf    │
   │                                    │── generateUploadSignedUrl ────►│
   │◄── { uploadUrl, objectName } ──────│◄── PUT URL (15min) ───────────│
   │                                    │                               │
   │── PUT <uploadUrl> ─────────────────────────────────────────────────►│
   │◄── 200 OK ──────────────────────────────────────────────────────────│
   │                                    │                               │
   │── POST /my/mangas/{id}/volumes/ ───►│── getBlob → md5, size ────────►│
   │   finalize                          │◄──────────────────────────────│
   │   { objectName, volumeNumber }      │ persiste Volume               │
   │◄── 201 Created { volume } ─────────│                               │
```

> **Promote:** `POST /my/mangas/{id}/promote` (COLLABORATOR+) move o mangá para `is_public=true`.
> O backend valida: título único na biblioteca pública, hashes dos volumes únicos na biblioteca pública,
> slug sem conflito. Não copia arquivos no GCS, só atualiza o flag `is_public`.

### 2.6 Inatividade automática

```
CLOUD SCHEDULER                  BACKEND                    POSTGRESQL         GCS
(diário às 02:00)                   │                            │               │
      │                             │                            │               │
      │── POST /admin/jobs/ ────────►│                            │               │
      │   inactivity                 │ valida X-Job-Secret        │               │
      │   X-Job-Secret: ...          │                            │               │
      │                             │── SELECT users WHERE ──────►│               │
      │                             │   status=ACTIVE             │               │
      │                             │◄── lista de usuários ───────│               │
      │                             │                            │               │
      │                             │  Para cada usuário:         │               │
      │                             │  ┌─ last_access < 75 dias   │               │
      │                             │  │  → envia e-mail aviso    │               │
      │                             │  │    (@Async, Gmail SMTP)  │               │
      │                             │  └─ last_access < 90 dias   │               │
      │                             │     → UPDATE status=INACTIVE►│               │
      │                             │     → busca mangas privados ►│               │
      │                             │     → deleta arquivos GCS ──────────────────►│
      │                             │     → deleta volumes no banco►│               │
      │                             │     → deleta mangas no banco ►│               │
      │◄── 200 OK ──────────────────│                            │               │
```

> O job também roda via `@Scheduled` interno (Spring) como fallback.
> Usuários ACTIVE são processados em páginas de 50 via `Pageable`, sem carregar toda a base em memória.

---

## 3. Modelo de dados resumido

### 3.1 Diagrama de entidades

```
User ──────────────────< Manga (owner_id)
                         │
                         ├──< Volume
                         │     └── file_url (objectName no GCS)
                         │         file_hash (MD5 via metadados GCS)
                         │
                         └──>──< Tag (via MangaTag)
                                  └──> TagCategory

Tag >──────────────────── TagCategory

User ──< ReadingProgress >────── Volume
User ──< ReadingHistory  >────── Volume
User ──< ReadingList     >────── Manga
User ──< Rating          >────── Manga
User ──< RefreshToken
```

### 3.2 Constraints e índices relevantes

| Tabela            | Constraint / Índice                       | Observação                              |
|-------------------|-------------------------------------------|-----------------------------------------|
| users             | UNIQUE(email), UNIQUE(username)           |                                         |
| mangas            | UNIQUE(slug)                              | Slug gerado com sufixo em conflito      |
| volumes           | UNIQUE(manga_id, volume_number)           | Por mangá, não global                   |
| volumes           | INDEX(file_hash)                          | Busca por duplicata no promote          |
| manga_tags        | PK(manga_id, tag_id)                      | Composite PK                            |
| reading_progress  | UNIQUE(user_id, volume_id)                | Upsert de progresso                     |
| reading_list      | UNIQUE(user_id, manga_id)                 |                                         |
| ratings           | UNIQUE(user_id, manga_id)                 | Uma avaliação por usuário por mangá     |
| refresh_tokens    | INDEX(user_id)                            | Lookup de tokens por usuário            |
| reading_history   | INDEX(user_id), INDEX(volume_id)          | V16 adicionou index em volume_id        |

### 3.3 Migrações Flyway (V1–V16)

| Versão | Descrição                                            |
|--------|------------------------------------------------------|
| V1     | Tabela users (enums role, status)                    |
| V2     | Tabela refresh_tokens                                |
| V3     | Tabela tag_categories                                |
| V4     | Tabela tags (soft delete com deleted_at)             |
| V5     | Tabela mangas (enums format, status_origin, status_site) |
| V6     | Tabela manga_tags (junction)                         |
| V7     | Tabela volumes                                       |
| V8     | Tabela reading_progress                              |
| V9     | Tabela reading_history                               |
| V10    | Tabela reading_list (enum status)                    |
| V11    | Tabela ratings (check score 1–5)                     |
| V12    | Seed: categorias de tags                             |
| V13    | Seed: 54+ tags iniciais                              |
| V14    | Adicionou UNIQUE(file_hash) em volumes               |
| V15    | Removeu UNIQUE(file_hash) — mangás privados podem ter mesmo hash |
| V16    | Adicionou INDEX(volume_id) em reading_history        |

---

## 4. Segurança e autenticação

### 4.1 Ciclo de vida do JWT + Refresh Token

```
Login
  │
  ├── accessToken  (JWT, assinado com JWT_SECRET)
  │   expira em:  JWT_EXPIRATION segundos (padrão: 3600 = 1h)
  │   contém:     userId, username, role
  │   usado em:   Authorization: Bearer <token>
  │
  └── refreshToken (UUID aleatório)
      expira em:  REFRESH_TOKEN_EXPIRATION segundos (padrão: 604800 = 7 dias)
      armazenado: tabela refresh_tokens (token + user_id + expires_at)
      usado em:   POST /auth/refresh para obter novo accessToken
      invalidado: no logout OU ao ser usado (rotação de token)

Refresh
  POST /auth/refresh { refreshToken }
  → backend busca token no banco
  → valida expires_at
  → deleta token antigo
  → gera novo refreshToken e persiste no banco
  → retorna { accessToken, refreshToken, expiresIn } (refresh token rotacionado)

Logout
  POST /auth/logout { refreshToken }
  → backend deleta token do banco
  → frontend limpa tokens armazenados
```

### 4.2 Rate limiting

Implementado em `RateLimitFilter` (in-memory `ConcurrentHashMap`):

| Endpoint              | Limite padrão   | Variável de env                  |
|-----------------------|-----------------|----------------------------------|
| POST /auth/register   | 5 req/hora      | RATE_LIMIT_REGISTER_PER_HOUR     |
| POST /auth/login      | 10 req/hora     | RATE_LIMIT_LOGIN_PER_HOUR        |

- IP detectado via header `X-Forwarded-For` (compatível com Cloud Run)
- Retorna `429 Too Many Requests` quando limite excedido
- Limpeza de entradas expiradas: `@Scheduled` a cada 1 hora

**hCaptcha no registro:** o endpoint `POST /auth/register` possui uma camada adicional de proteção anti-bot via hCaptcha. O frontend renderiza o widget e envia o token resolvido no campo `captchaToken`; o backend valida o token na API `https://api.hcaptcha.com/siteverify` antes de criar o usuário. Em desenvolvimento (sem `HCAPTCHA_SECRET` configurado), a validação é ignorada automaticamente.

### 4.3 Controle de acesso por role

| Ação                                     | Visitante | READER | COLLABORATOR | ADMIN |
|------------------------------------------|-----------|--------|--------------|-------|
| Registro e login                         | ✅        | —      | —            | —     |
| Biblioteca (listagem, detalhes)          | ❌        | ✅     | ✅           | ✅    |
| Leitor de PDF (público)                  | ❌        | ✅     | ✅           | ✅    |
| Lista de leitura e avaliações            | ❌        | ✅     | ✅           | ✅    |
| Coleção privada (criar, editar, deletar) | ❌        | ✅     | ✅           | ✅    |
| Criar mangá público                      | ❌        | ❌     | ✅           | ✅    |
| Upload de volume público                 | ❌        | ❌     | ✅           | ✅    |
| Promover privado → público               | ❌        | ❌     | ✅           | ✅    |
| Painel admin (usuários, dashboard)       | ❌        | ❌     | ❌           | ✅    |
| Gerenciar tags e categorias              | ❌        | ❌     | ❌           | ✅    |
| Alterar role/status/cota de usuários     | ❌        | ❌     | ❌           | ✅    |
| Enviar feedback (`POST /feedback`)       | ❌        | ✅     | ✅           | ✅    |

> Usuários com status `PENDING` ou `INACTIVE` são bloqueados no login.

### 4.4 URLs assinadas do GCS (V4, ServiceAccountCredentials)

| Tipo                   | Método HTTP | Expiração | Quem usa                                    |
|------------------------|-------------|-----------|---------------------------------------------|
| Leitura de PDF         | GET         | 30 min    | Leitor no browser                           |
| Leitura de capa privada| GET         | 1 hora    | Tela de coleção privada                     |
| Upload de volume       | PUT         | 15 min    | Frontend (PUT direto ao GCS)                |

- URLs geradas pelo backend com credenciais de service account
- O browser acessa o GCS diretamente, sem passar pelo backend
- Após expiração: GCS retorna `403 Forbidden`
- O header `Authorization` **não** deve ser enviado ao GCS (quebraria a assinatura)

### 4.5 CORS

**Backend (Spring Security, `SecurityConfig`):**

| Origem permitida                                                    | Métodos                              |
|---------------------------------------------------------------------|--------------------------------------|
| `http://localhost:5173` (dev)                                       | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| `http://localhost:3000` (dev)                                       | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| Valor de `APP_CORS_ALLOWED_ORIGIN` (produção: URL do Cloud Run)     | GET, POST, PUT, PATCH, DELETE, OPTIONS |

> `allowCredentials: true`, necessário para envio de cookies e Authorization header.

**GCS Bucket (`gcs-cors.json`):**

| Origem permitida                                                             | Métodos           | Max-Age |
|------------------------------------------------------------------------------|-------------------|---------|
| `https://buruna.com.br`                                                      | GET, PUT, HEAD    | 3600s   |
| `https://buruna-frontend-922749062176.us-east1.run.app`                      | GET, PUT, HEAD    | 3600s   |

> PUT necessário para upload direto. GET e HEAD para leitura de PDF pelo browser.
> Pendente: adicionar `https://buruna.com.br` no CORS do backend quando o domínio propagar.

---

## 5. Decisões de design — Architecture Decision Records (ADRs)

---

### ADR-01 — Monolito modular (Spring Boot)

**Contexto:** Projeto solo, sem deadline, sem equipe distribuída. Todos os módulos (auth, manga, reader, admin) compartilham o mesmo banco e o mesmo ciclo de deploy.  
**Decisão:** Monolito modular com separação por pacotes (`auth/`, `manga/`, `reader/`, etc.) em vez de microsserviços.  
**Por quê:** Microsserviços adicionam uma pilha de complexidade operacional que não faz sentido para um dev sozinho: service discovery, comunicação inter-serviço, deploys independentes, tracing distribuído. O monolito com pacotes bem separados mantém boundaries claros entre domínios e dá liberdade para extrair serviços no futuro, se a necessidade aparecer, sem pagar esse custo agora.  
**Tradeoff:** Se o projeto escalar para vários devs trabalhando em paralelo, o monolito pode gerar conflitos de merge e deploys acoplados. Aceitável no momento.

---

### ADR-02 — ~~Upload via backend (multipart)~~ → Substituída por ADR-24

**Status:** Substituída  
**Contexto original:** Na primeira versão, o upload de volumes passava inteiro pelo backend (multipart/form-data). O backend recebia o arquivo, processava e mandava pro GCS.  
**Por que mudou:** O Cloud Run tem limite de memória e tempo de request. PDFs grandes (300–600 MB) causavam timeouts e comiam memória do container. A ADR-24 substituiu isso por upload direto ao GCS via Signed URL.

---

### ADR-03 — @Async e @Scheduled internos

**Contexto:** O projeto precisa de envio de e-mail assíncrono (cadastro, aprovação, inatividade) e de um job diário de inatividade.  
**Decisão:** Usar `@Async` do Spring para e-mails e `@Scheduled` para o job, em vez de Kafka, RabbitMQ ou outra fila.  
**Por quê:** O volume de eventos assíncronos é baixíssimo: dezenas de e-mails por semana, um job por dia. Meter um message broker no meio é um serviço a mais pra operar, monitorar e pagar. O `@Async` resolve sem infraestrutura adicional. O Cloud Scheduler entra como trigger externo redundante pro job, cobrindo o caso em que o container escala pra zero e o `@Scheduled` interno não chega a rodar.  
**Tradeoff:** Se o `@Async` falhar (exceção no envio de e-mail), não tem retry automático nem dead letter queue. Com o volume atual, ok. E-mails perdidos são raros e não travam nada.

---

### ADR-04 — nginx como reverse proxy no frontend

**Contexto:** O frontend é uma SPA React que precisa de: servir arquivos estáticos, fazer proxy de `/api/*` para o backend, e suportar client-side routing (fallback `try_files`).  
**Decisão:** Container Docker com nginx servindo o build estático e fazendo proxy pass para o backend via VPC connector.  
**Por quê:** O nginx é leve (~5 MB de imagem), sólido pra servir estáticos e fazer proxy, e não precisa de Node.js em produção. A alternativa seria um API Gateway externo (tipo o GCP API Gateway), que ia adicionar custo, latência e configuração para um ganho mínimo quando o projeto tem um backend só.  
**Tradeoff:** Sem features de API Gateway (rate limiting centralizado, API keys, transformações de request). O que é necessário disso está implementado no próprio backend.

---

### ADR-05 — Paginação offset/page

**Contexto:** Listagens de mangás, usuários, histórico de leitura, tudo precisa de paginação.  
**Decisão:** Paginação offset-based com `Pageable` do Spring Data (`?page=0&size=20`).  
**Por quê:** Implementação trivial com Spring Data JPA. O dataset é pequeno (centenas de mangás, dezenas de usuários) e as listagens são ordenadas por campos indexados. Cursor-based pagination seria necessária com milhões de registros ou feeds infinitos com inserções frequentes. Nenhum desses casos existe no Burūna.  
**Tradeoff:** Em datasets grandes, `OFFSET` tem performance O(n), o banco percorre N registros antes de retornar. Irrelevante no tamanho atual.

---

### ADR-06 — BCrypt para hashing de senhas
 
**Contexto:** Senhas de usuários precisam ser armazenadas de forma segura.  
**Decisão:** BCrypt via `PasswordEncoder` do Spring Security.  
**Por quê:** BCrypt é um algoritmo de hashing adaptativo feito especificamente para senhas. Inclui salt automático e work factor configurável que torna brute force impraticável. SHA-256, por outro lado, é rápido por design, que é o oposto do que você quer para senhas (facilita brute force). O Spring Security já vem com BCrypt como implementação padrão, sem config extra.  
**Tradeoff:** BCrypt é mais lento por request de login comparado a SHA-256 (~100ms vs ~1ms). Esse custo é intencional e imperceptível pro usuário.

---

### ADR-07 — GCS com URLs assinadas (V4)
 
**Contexto:** PDFs e capas precisam ser acessíveis pelo browser, mas não podem ser públicos pra qualquer um na internet.  
**Decisão:** Bucket GCS privado. O backend gera URLs assinadas V4 com expiração por operação (leitura PDF: 30 min, capa privada: 1h, upload PUT: 15 min). O browser acessa o GCS direto com a URL assinada.  
**Por quê:** URLs públicas no bucket significaria que qualquer pessoa com o link baixa qualquer PDF, inaceitável pra uma biblioteca que exige cadastro aprovado. URLs assinadas dão acesso temporário e controlado sem que o backend precise servir os bytes (caro em memória e bandwidth no Cloud Run). A expiração curta limita a janela de exposição de cada link.  
**Tradeoff:** URLs assinadas não podem ser revogadas antes da expiração. Se um link vazar, ele funciona até expirar. Mitigado pelas expirações curtas (15–30 min).

---

### ADR-08 — UptimeRobot para monitoramento
 
**Contexto:** Precisa de alerta quando o site cai.  
**Decisão:** UptimeRobot (free tier) com check HTTP a cada 5 minutos e alerta por e-mail.  
**Por quê:** Custo zero, configura em 2 minutos. A alternativa seria Cloud Monitoring, Prometheus ou algo do tipo, que adiciona complexidade e possivelmente custo pra resolver um problema que o UptimeRobot já resolve. Pra um projeto solo, saber que o site caiu em até 5 minutos é o bastante.  
**Tradeoff:** Sem métricas de performance, sem alertas de latência, sem dashboards. Se o projeto crescer, migrar pra algo mais completo.

---

### ADR-09 — Índices seletivos no banco
 
**Contexto:** PostgreSQL cria índices automaticamente para PKs e UNIQUEs, mas FKs e colunas usadas em WHERE/ORDER BY precisam de índices manuais.  
**Decisão:** Criar índices só nas colunas com queries frequentes e alta cardinalidade (7 índices manuais em FKs de leitura pesada como `reading_progress.user_id`, `reading_history.volume_id`, etc.). Não indexar toda FK de forma automática.  
**Por quê:** Cada índice tem custo de escrita (INSERT/UPDATE mais lentos) e de storage. Com um dataset pequeno, a maioria das queries é rápida mesmo sem índice. Criamos índices onde o query plan mostrou sequential scans em tabelas que crescem proporcionalmente ao uso (leitura, progresso, histórico). FKs em tabelas pequenas e raramente filtradas (como `manga_tags` com composite PK) não precisam de índice extra.  
**Tradeoff:** Queries em colunas não indexadas podem ficar lentas se o dataset crescer de forma inesperada. Monitorar e adicionar índices conforme a necessidade.

---

### ADR-10 — Rate limit + hCaptcha no registro
**Contexto:** O endpoint de registro é público e precisa de proteção contra bots e abuso.
**Decisão:** Rate limit de 5 requests/hora por IP via `RateLimitFilter` (in-memory `ConcurrentHashMap`) combinado com validação de hCaptcha em `CaptchaService`. O frontend renderiza o widget `@hcaptcha/react-hcaptcha` e envia o token resolvido no campo `captchaToken`; o backend valida via `POST https://api.hcaptcha.com/siteverify`. Em desenvolvimento (sem `HCAPTCHA_SECRET`), a chamada à API é ignorada.
**Por quê:** O rate limit barra abuso de volume (DDoS no endpoint, spam de e-mails ao admin). O hCaptcha impede bots sofisticados que respeitam rate limits — a defesa certa contra automações que criariam uma conta a cada 12 minutos por IP.
**Tradeoff:** Adiciona fricção pra usuários legítimos e dependência de serviço externo (hCaptcha). O captcha expira após alguns minutos, mas o widget exibe reset automático via `onExpire`.

---

### ADR-11 — alternative_titles e content_warnings como TEXT (JSON serializado)
 
**Contexto:** Mangás podem ter múltiplos títulos alternativos e avisos de conteúdo. Precisa armazenar listas de strings.  
**Decisão:** Colunas `TEXT` contendo JSON serializado (ex: `["Attack on Titan", "Shingeki no Kyojin"]`) em vez de `TEXT[]` nativo do PostgreSQL.  
**Por quê:** `TEXT[]` do PostgreSQL não tem suporte nativo no Hibernate/JPA sem config extra (converters, tipos customizados, dependência do `hibernate-types`). JSON em TEXT é serializável/desserializável sem dor de cabeça com Jackson, funciona com qualquer ORM, e é portável pra outros bancos se necessário. As queries nesses campos são `LIKE` em texto, que funciona igual em JSON serializado e em arrays.  
**Tradeoff:** Perde operações nativas de array do PostgreSQL (`ANY`, `@>`, indexação GIN). Pro caso de uso atual (filtro por substring e exibição), não faz diferença.

---

### ADR-12 — Filtro de tags com semântica AND
 
**Contexto:** O usuário filtra mangás por tags (ex: "Ação" + "Seinen"). A query precisa decidir se retorna mangás que têm *todas* as tags selecionadas (AND) ou *qualquer uma* delas (OR).  
**Decisão:** Semântica AND. O mangá precisa ter todas as tags selecionadas pra aparecer no resultado.  
**Por quê:** O caso de uso é refinar busca. Se o usuário seleciona "Ação" + "Seinen", quer mangás que são ambos, não qualquer mangá de ação ou qualquer seinen. OR amplia demais os resultados e torna os filtros quase inúteis quando o usuário marca mais de uma tag. MangaDex e Anilist usam AND como padrão.  
**Tradeoff:** Combinações muito específicas de tags podem retornar zero resultados. O frontend pode mitigar mostrando a contagem de resultados em tempo real enquanto o usuário adiciona tags.

---

### ADR-13 — GET /mangas/{slugOrId} resolve UUID ou slug no mesmo endpoint
 
**Contexto:** Mangás podem ser acessados por slug (URL amigável) ou por UUID (referência interna). A questão era se seriam endpoints separados ou um único.  
**Decisão:** Endpoint único que detecta se o parâmetro é UUID (tenta `UUID.fromString()`) ou slug (qualquer outra string).  
**Por quê:** Simplifica o roteamento no frontend, que sempre usa `/mangas/{valor}` sem precisar saber se é ID ou slug. Menos duplicação de código no controller. A detecção é trivial e sem ambiguidade (UUIDs têm formato único com hífens e hex).  
**Tradeoff:** Se algum dia um slug acabar sendo um UUID válido, ia dar conflito. Na prática, improvável: slugs são gerados a partir de títulos e nunca têm formato UUID.

---

### ADR-14 — client_max_body_size 600M no nginx + proxy_request_buffering off
 
**Contexto:** Volumes de mangá podem ter centenas de MB. O nginx precisa aceitar requests grandes para o proxy pass ao backend.  
**Decisão:** `client_max_body_size 600M` e `proxy_request_buffering off` na config do nginx.  
**Por quê:** O limite padrão do nginx é 1 MB, insuficiente pra uploads de PDF. 600 MB cobre até os maiores tankōbon digitais. O `proxy_request_buffering off` evita que o nginx armazene o corpo inteiro em disco/memória antes de encaminhar, fazendo streaming direto pro backend. Sem isso, o container do frontend estoura memória.  
**Nota:** Depois da ADR-24 (upload direto ao GCS), uploads grandes não passam mais pelo nginx. O `client_max_body_size` alto ficou como safety net mas raramente é exercitado. Os maiores payloads via nginx agora são JSONs de metadados.

---

### ADR-15 — TagSelector oculta "Aviso de Conteúdo" via excludeCategories
 
**Contexto:** Tags de "Aviso de Conteúdo" (gore, violência, etc.) existem no banco pra marcar mangás, mas não devem aparecer como filtro na busca da biblioteca. Só no formulário de edição/criação.  
**Decisão:** O componente `TagSelector` recebe uma prop `excludeCategories` e oculta as categorias listadas. Sem alteração no banco.  
**Por quê:** Remover a categoria do banco eliminaria a funcionalidade de marcar conteúdo sensível, que é importante pra experiência do leitor. A filtragem no frontend é simples, flexível (diferentes telas excluem categorias diferentes) e não precisa de lógica no backend.  
**Tradeoff:** A categoria ainda é retornada pela API, com overhead mínimo de dados. Se necessário, dá pra adicionar um parâmetro `excludeCategories` na API no futuro.

---

### ADR-16 — Two-step fetch em findPublic (paginar + batch load de tags)
 
**Contexto:** A listagem de mangás públicos precisa retornar mangás com suas tags. Usar `@EntityGraph` com `Pageable` na mesma query JPA gera o clássico "HHH000104: firstResult/maxResults with collection fetch, applying in memory". O Hibernate carrega tudo e pagina em memória.  
**Decisão:** Duas queries: (1) paginação dos mangás sem tags, (2) batch load das tags dos mangás retornados via `WHERE manga_id IN (...)`.  
**Por quê:** A alternativa (`@EntityGraph + Pageable`) potencialmente carrega todos os mangás em memória pra depois paginar, anulando o propósito da paginação. O two-step fetch mantém a paginação real no banco e carrega tags só dos mangás que vão ser exibidos. Uma query a mais é bem melhor que risco de OOM com datasets grandes.  
**Tradeoff:** Duas queries em vez de uma. Pra N=20 mangás por página, o overhead é irrelevante (~1ms extra).

---

### ADR-17 — Remoção do UNIQUE(file_hash) em V15
 
**Contexto:** A V14 adicionou `UNIQUE(file_hash)` na tabela `volumes` pra detectar uploads duplicados. Só que mangás privados de usuários diferentes podem ter o mesmo PDF (ex: mesmo capítulo baixado da mesma fonte).  
**Decisão:** Remover a constraint global `UNIQUE(file_hash)` em V15. A verificação de duplicata agora é feita no código, só no momento do promote (privado → público).  
**Por quê:** Coleções privadas são independentes. Se dois usuários fazem upload do mesmo arquivo na coleção pessoal, não tem conflito. Cada um tem seu espaço. A unicidade de hash só importa na biblioteca pública, onde duplicatas desperdiçam espaço e confundem a navegação. Mover a validação pro service do promote permite verificar só contra mangás públicos (ver ADR-18).  
**Tradeoff:** Volumes com hash duplicado podem existir no bucket GCS (arquivos diferentes, mesmo conteúdo). O custo de storage é mínimo.

---

### ADR-18 — Promote valida unicidade só contra mangás públicos
 
**Contexto:** Ao promover um mangá privado pra público, precisa verificar se não há conflitos de título e hash de volumes.  
**Decisão:** Validar unicidade de título e hashes de volumes só contra mangás com `is_public = true`.  
**Por quê:** Consequência direta da ADR-17. Mangás privados são invisíveis entre si. Se "One Piece Vol 1" existe em duas coleções privadas e um é promovido, o outro continua privado e não gera conflito. Validar contra todos os mangás (públicos + privados) impediria promotes legítimos e exporia informações sobre coleções de outros usuários, o que seria uma violação de privacidade.

---

### ADR-19 — pdfjs-dist v4 (não v5)
 
**Contexto:** O leitor de mangá usa pdfjs-dist pra renderizar PDFs no browser.  
**Decisão:** Manter pdfjs-dist v4 em vez de migrar pra v5.  
**Por quê:** A v5 introduziu breaking changes na API (Worker API reformulada, importação de módulos, remoção de compatibilidade com builds legacy) que exigiriam refatoração pesada do componente de leitura. A v4 é estável, atende tudo que o leitor precisa, e recebe patches de segurança. Migrar pra v5 pode fazer sentido no futuro se alguma feature nova exclusiva da v5 justificar o esforço.  
**Tradeoff:** A v4 vai entrar em end-of-life eventualmente. Monitorar e planejar migração quando patches de segurança pararem.

---

### ADR-20 — Worker pdfjs servido localmente via public/
 
**Contexto:** O pdfjs-dist precisa de um Web Worker pra renderizar PDFs. Esse worker pode vir de um CDN público ou ser servido localmente.  
**Decisão:** Worker copiado pra `public/` e servido pelo próprio nginx do frontend.  
**Por quê:** CDNs (unpkg, jsdelivr) são uma dependência externa em runtime. Se o CDN cair ou mudar a URL, o leitor para de funcionar. Servir localmente garante que o worker está disponível enquanto o frontend estiver no ar. O custo de bandwidth é irrelevante (o worker tem ~500 KB e o browser cacheia).  
**Tradeoff:** Atualizar a versão do worker exige rebuild do frontend. Aceitável, já que atualizar pdfjs já exige rebuild de qualquer forma.

---

### ADR-21 — Progresso de leitura por volume, não por mangá
 
**Contexto:** O endpoint de progresso poderia retornar o progresso de um volume específico (`GET /reader/{volumeId}/progress`) ou o progresso mais recente do mangá inteiro (`GET /reader/progress/{mangaId}`).  
**Decisão:** Progresso por volume. Existe também `GET /reader/progress/{mangaId}` como atalho pra retomar leitura, mas a fonte de verdade é o volume.  
**Por quê:** Progresso é inerentemente por volume, cada um tem número de páginas diferente. Armazenar por mangá exigiria lógica extra pra descobrir "qual volume estava sendo lido" e "qual página daquele volume". Por volume é direto: `user_id + volume_id → currentPage`. O endpoint por mangá só busca o `ReadingProgress` mais recente do usuário pra aquele mangá e redireciona.  
**Tradeoff:** Pra retomar leitura a partir da tela de detalhes do mangá, precisa de uma query extra pra achar o volume mais recente. Resolvido com o endpoint de atalho.

---

### ADR-22 — Cloud Run separado para frontend e backend
 
**Contexto:** O deploy poderia ser: (a) dois serviços Cloud Run separados (frontend + backend) ou (b) um GCE único rodando Docker Compose com ambos.  
**Decisão:** Cloud Run separado pra cada um.  
**Por quê:** Cloud Run escala pra zero. Quando ninguém acessa, custo zero. Com GCE, o VM fica ligado 24/7 mesmo sem tráfego. Pra um projeto com picos esporádicos de uso, o modelo serverless do Cloud Run sai muito mais barato. Fora isso, deploys independentes permitem atualizar frontend e backend separadamente sem downtime (Cloud Run faz blue-green deployment automático por revisão).  
**Tradeoff:** Cold starts no Cloud Run (~2–5s no primeiro request depois de escalar de zero). Mitigado com UptimeRobot fazendo ping a cada 5 minutos, mantendo pelo menos uma instância quente. O PostgreSQL continua em GCE (ver ADR-23) porque precisa de persistência em disco.

---

### ADR-23 — PostgreSQL no GCE e2-micro com Docker
 
**Contexto:** O banco precisa de persistência e disponibilidade. As opções eram Cloud SQL (managed) ou PostgreSQL self-hosted em GCE.  
**Decisão:** PostgreSQL 16 em container Docker rodando numa instância GCE e2-micro (free tier permanente do GCP).  
**Por quê:** Cloud SQL db-f1-micro custa ~$10/mês. O GCE e2-micro é de graça no free tier permanente do GCP. Pra um projeto com dezenas de usuários e queries simples, a performance do e2-micro dá conta. O PostgreSQL em Docker é fácil de configurar, fazer backup (pg_dump via cron) e migrar.  
**Tradeoff:** Sem alta disponibilidade (single point of failure), sem backups automáticos gerenciados, sem patching automático do PostgreSQL. O dev precisa gerenciar backups e atualizações na mão. Pro tamanho do projeto, o risco é aceitável. Recovery de um pg_dump leva minutos.

---

### ADR-24 — Upload direto ao GCS via Signed URL (PUT)
(substitui ADR-02)  
**Contexto:** A ADR-02 original fazia upload via backend (multipart). O backend recebia o PDF inteiro, guardava em memória/disco temporário, e reenviava ao GCS. Com PDFs de centenas de MB, isso causava timeouts e uso excessivo de memória no Cloud Run.  
**Decisão:** Fluxo de duas fases: (1) backend gera uma Signed URL de PUT com expiração de 15 min, (2) frontend faz PUT direto ao GCS com o arquivo, (3) frontend chama endpoint de finalize no backend pra persistir metadados.  
**Por quê:** O backend nunca toca o arquivo, só gera a URL e valida metadados depois do upload. Isso elimina o gargalo de memória e bandwidth no Cloud Run, permite uploads de qualquer tamanho sem timeout, e reduz latência (o browser faz upload direto ao bucket em southamerica-east1, perto do usuário). O finalize consulta só os metadados do blob no GCS (md5, tamanho) sem baixar o arquivo.  
**Tradeoff:** Se o upload acontecer mas o finalize não for chamado, o arquivo fica órfão no GCS. Dá pra resolver com lifecycle rule (no backlog) ou job de limpeza periódico.

---

### ADR-25 — Hash via blob.getMd5() dos metadados do GCS
 
**Contexto:** Na detecção de duplicatas, precisa do hash do arquivo uploadado. As opções: (a) baixar o arquivo no backend e calcular SHA-256, ou (b) usar o MD5 que o GCS calcula automaticamente no upload.  
**Decisão:** Usar `blob.getMd5()` dos metadados do GCS no endpoint de finalize.  
**Por quê:** O GCS calcula o MD5 de todo objeto no momento do upload e armazena nos metadados, disponível via API sem baixar o arquivo. Baixar centenas de MB no backend pra calcular SHA-256 local anularia o benefício da ADR-24 (upload direto). O MD5 não é criptograficamente seguro contra colisões intencionais, mas pra detecção de duplicatas acidentais (mesmo PDF uploadado duas vezes) é mais do que o necessário. A probabilidade de colisão acidental em MD5 é ~10⁻³⁸.  
**Tradeoff:** MD5 é vulnerável a colisões intencionais. Se um atacante quisesse fazer upload de dois arquivos diferentes com mesmo MD5, conseguiria. No contexto do Burūna (biblioteca pessoal com cadastro aprovado), esse cenário não é realista.

---

### ADR-26 — GCS bucket em southamerica-east1, Cloud Run em us-east1
 
**Contexto:** O bucket GCS armazena PDFs acessados diretamente pelo browser. Os serviços Cloud Run (backend/frontend) rodam em us-east1.  
**Decisão:** Bucket em `southamerica-east1` (São Paulo). Cloud Run em `us-east1` (Carolina do Sul).  
**Por quê:** Os PDFs são acessados direto pelo browser via Signed URL. O Cloud Run não faz proxy do conteúdo. Então a latência que importa é browser → GCS, não Cloud Run → GCS. Com o bucket em São Paulo, usuários brasileiros (público-alvo) têm latência baixa no download de PDFs (~20ms vs ~120ms pra us-east1). O Cloud Run fica em us-east1 porque oferece free tier generoso e os requests de API são leves (JSONs de poucos KB). A única comunicação Cloud Run → GCS é no finalize (leitura de metadados, poucos KB, ~100ms de latência cross-region, uma vez por upload).  
**Tradeoff:** Latência cross-region de ~100ms na comunicação backend → GCS (só pra operações administrativas: gerar Signed URL, ler metadados no finalize). Imperceptível pro usuário, que já está esperando o upload terminar.

### ADR-27 — Canvas do pdfjs escalado por devicePixelRatio + escala mínima

**Contexto:** O pdfjs renderizava o canvas em resolução 1x independente do dispositivo. Em celulares com tela de alta densidade (DPR 2x ou 3x), isso deixava texto e imagens visivelmente borrados. O modo responsivo do DevTools no desktop não reproduzia o problema — só aparecia em celulares reais.
**Decisão:** Multiplicar a escala de renderização pelo `devicePixelRatio` do dispositivo (limitado a 3) e forçar um piso de 1.5 na escala base. O truque é usar escalas diferentes para o canvas: `canvas.width/height` recebe a escala alta (mais pixels reais), enquanto `canvas.style.width/height` mantém a escala original (tamanho visual inalterado).
**Justificativa:** Só aplicar o DPR não bastou — em telas estreitas, a escala base calculada era tão baixa que mesmo multiplicada por 3 o resultado ficava pobre. O piso de 1.5 resolve isso garantindo uma resolução mínima de renderização. A separação entre resolução e tamanho visual é o que permite renderizar em alta qualidade sem alterar o layout da página.
**Tradeoff aceito:** O canvas usa mais memória no celular (entre 3x e 4.5x mais pixels). Na prática não causou problemas — se um dia causar em devices com pouca RAM, o cap de 3 no DPR já limita o pior caso.

### ADR-28 — Refresh token rotation no /auth/refresh

Já implementado desde o MVP
**Contexto:** Sem rotation, um refresh token roubado dava acesso por 7 dias inteiros — o atacante e o usuário legítimo podiam usar o mesmo token em paralelo sem que nenhum dos dois percebesse.
**Decisão:** Cada chamada a `POST /auth/refresh` deleta o token usado e gera um novo. O response devolve accessToken + refreshToken novos, e o frontend atualiza os dois no storage.
**Justificativa:** Se alguém roubar o token e usá-lo, o original morre. Na próxima vez que o usuário legítimo tentar renovar, o token dele já não existe — recebe 401 e precisa relogar. Não é perfeito (o atacante ainda usou uma vez), mas a janela de exploração cai de 7 dias para um único ciclo.
**Tradeoff aceito:** Se por algum motivo o mesmo refresh token for enviado duas vezes (ex: resposta de rede duplicada, retry automático), a segunda chamada dá 401 e força logout. O interceptor Axios do frontend evita isso com uma fila que serializa chamadas ao `/auth/refresh`, então na prática não acontece.