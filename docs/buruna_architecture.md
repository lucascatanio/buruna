# Burūna — Arquitetura e Fluxogramas

> Documento de referência para quem vai trabalhar no projeto sem contexto prévio.
> Descreve a infraestrutura GCP, os fluxos de usuário completos, o modelo de dados,
> a segurança e um resumo das decisões de arquitetura.

---

## 1. Visão Geral da Infraestrutura

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
│  Artifact Registry (us-east1)                                                │
│  buruna-backend:latest                                                       │
│  buruna-frontend:latest                                                      │
│                ↓ deploy via gcloud run deploy                                │
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
| Arquivos PDF/capas  | GCS `buruna-files-catanio`           | southamerica-east1 | Latência mínima para usuários BR    |
| Jobs agendados      | Cloud Scheduler                      | us-east1           | Trigger diário do InactivityJob     |
| Imagens Docker      | Artifact Registry                    | us-east1           | Pipeline de CI/deploy               |
| Secrets             | Secret Manager                       | us-east1           | Injetados no Cloud Run              |
| Monitoramento       | UptimeRobot                          | —                  | Alerta de downtime por e-mail       |
| Domínio             | buruna.com.br (registro.br)          | —                  | TLS automático via Cloud Run        |

---

## 2. Fluxos de Usuário Completos

### 2.1 Cadastro e Aprovação

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

### 2.3 Leitura de Mangá

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

> Se a signed URL expirar durante a leitura (após 30 min), o browser recebe 403 do GCS.
> O frontend deve detectar o erro e solicitar nova URL via `GET /reader/{volumeId}/url`.

### 2.4 Upload de Volume Público (duas fases)

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

### 2.5 Upload Privado (criar mangá + volume)

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
> slug sem conflito. Não copia arquivos no GCS — apenas atualiza o flag `is_public`.

### 2.6 Inatividade Automática

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

> O job também é acionado via `@Scheduled` interno (Spring) como fallback.
> Limitação atual: não usa paginação — adequado para ≤100 usuários.

---

## 3. Modelo de Dados Resumido

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
| V15    | Removeu UNIQUE(file_hash) — mangas privados podem ter mesmo hash |
| V16    | Adicionou INDEX(volume_id) em reading_history        |

---

## 4. Segurança e Autenticação

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
  → retorna novo accessToken (e opcionalmente novo refreshToken)

Logout
  POST /auth/logout { refreshToken }
  → backend deleta token do banco
  → frontend limpa tokens armazenados
```

### 4.2 Rate Limiting

Implementado em `RateLimitFilter` (in-memory `ConcurrentHashMap`):

| Endpoint              | Limite padrão   | Variável de env                  |
|-----------------------|-----------------|----------------------------------|
| POST /auth/register   | 5 req/hora      | RATE_LIMIT_REGISTER_PER_HOUR     |
| POST /auth/login      | 10 req/hora     | RATE_LIMIT_LOGIN_PER_HOUR        |

- IP detectado via header `X-Forwarded-For` (compatível com Cloud Run)
- Retorna `429 Too Many Requests` quando limite excedido
- Limpeza de entradas expiradas: `@Scheduled` a cada 1 hora

### 4.3 Controle de Acesso por Role

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

> Usuários com status `PENDING` ou `INACTIVE` são bloqueados no login.

### 4.4 URLs Assinadas do GCS (V4 — ServiceAccountCredentials)

| Tipo                   | Método HTTP | Expiração | Quem usa                                    |
|------------------------|-------------|-----------|---------------------------------------------|
| Leitura de PDF         | GET         | 30 min    | Leitor no browser                           |
| Leitura de capa privada| GET         | 1 hora    | Tela de coleção privada                     |
| Upload de volume       | PUT         | 15 min    | Frontend (PUT direto ao GCS)                |

- URLs geradas pelo backend com credenciais de service account
- O browser acessa o GCS diretamente — sem passar pelo backend
- Após expiração: GCS retorna `403 Forbidden`
- O header `Authorization` **não** deve ser enviado ao GCS (quebraria a assinatura)

### 4.5 CORS

**Backend (Spring Security — `SecurityConfig`):**

| Origem permitida                                                    | Métodos                              |
|---------------------------------------------------------------------|--------------------------------------|
| `http://localhost:5173` (dev)                                       | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| `http://localhost:3000` (dev)                                       | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| Valor de `APP_CORS_ALLOWED_ORIGIN` (produção: URL do Cloud Run)     | GET, POST, PUT, PATCH, DELETE, OPTIONS |

> `allowCredentials: true` — necessário para envio de cookies e Authorization header.

**GCS Bucket (`gcs-cors.json`):**

| Origem permitida                                                             | Métodos           | Max-Age |
|------------------------------------------------------------------------------|-------------------|---------|
| `https://buruna.com.br`                                                      | GET, PUT, HEAD    | 3600s   |
| `https://buruna-frontend-922749062176.us-east1.run.app`                      | GET, PUT, HEAD    | 3600s   |

> PUT é necessário para upload direto. GET e HEAD para leitura de PDF pelo browser.
> Pendente: adicionar `https://buruna.com.br` no CORS do backend quando o domínio propagar completamente.

---

## 5. Decisões de Design — Resumo das ADRs

| #  | Decisão tomada                                                          | Alternativa descartada                          |
|----|-------------------------------------------------------------------------|-------------------------------------------------|
| 1  | Monolito modular (Spring Boot)                                          | Microsserviços                                  |
| 2  | ~~Upload via backend~~ (superseded por ADR 24)                          | Upload direto ao GCS                            |
| 3  | Async/Scheduler internos (@Async, @Scheduled)                           | Kafka / fila de mensagens                       |
| 4  | nginx reverse proxy                                                     | API Gateway externo                             |
| 5  | Paginação offset/page                                                   | Cursor-based pagination                         |
| 6  | BCrypt para senhas                                                      | SHA-256 puro                                    |
| 7  | GCS URLs assinadas para arquivos                                        | URLs públicas no bucket                         |
| 8  | UptimeRobot para monitoramento                                          | Solução custom de health check                  |
| 9  | Índices seletivos (7 índices em FKs/colunas de alta frequência)         | Índice em toda FK e coluna filtrada             |
| 10 | Rate limit sem Captcha no cadastro                                      | Rate limit + hCaptcha                           |
| 11 | alternative_titles/content_warnings como TEXT (JSON)                   | TEXT[] nativo do PostgreSQL                     |
| 12 | Filtro de tags com semântica AND                                        | Semântica OR                                    |
| 13 | GET /mangas/{slugOrId} resolve UUID ou slug no mesmo endpoint           | Rota separada GET /mangas/id/{id}               |
| 14 | client_max_body_size 600M no nginx + proxy_request_buffering off        | Limite padrão de 1MB                            |
| 15 | TagSelector oculta "Aviso de Conteúdo" via excludeCategories            | Remover categoria do banco                      |
| 16 | Two-step fetch em findPublic (paginar + batch load de tags)             | @EntityGraph + Pageable na mesma query          |
| 17 | Constraint UNIQUE(file_hash) removida em V15                            | Manter constraint global de hash               |
| 18 | Promote valida unicidade apenas contra mangás públicos                  | Validar contra todos os mangás                  |
| 19 | pdfjs-dist v4 (não v5)                                                  | pdfjs-dist v5 (latest)                          |
| 20 | Worker pdfjs servido localmente via public/                             | CDN (unpkg/jsdelivr)                            |
| 21 | GET /reader/{volumeId}/progress (por volume, não por mangá)             | GET /reader/progress/{mangaId} (volume recente) |
| 22 | Cloud Run separado para frontend e backend                              | GCE com Docker Compose único                    |
| 23 | PostgreSQL no GCE e2-micro com Docker                                   | Cloud SQL db-f1-micro (~$10/mês)                |
| 24 | Upload direto GCS via Signed URL (PUT) — backend só gera URL e finaliza | Upload via backend (multipart) — ADR 2          |
| 25 | Hash via blob.getMd5() dos metadados do GCS no finalize                 | storage.readAllBytes() + SHA-256 local          |
| 26 | GCS bucket em southamerica-east1; Cloud Run em us-east1                 | Co-localizar bucket em us-east1                 |
