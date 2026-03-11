# Burūna — DDS (Design Document Specification)

> Documento técnico de referência para o desenvolvimento.
> Consolida arquitetura, módulos, contratos de API, modelo de dados e fluxos críticos.

---

## 1. Visão Geral da Arquitetura

```
┌─────────────────────────────────────────────────────┐
│                    CLIENTE                          │
│         React + shadcn/ui (SPA)                     │
│         Desktop / Mobile                            │
└─────────────────────┬───────────────────────────────┘
                      │ HTTPS
┌─────────────────────▼───────────────────────────────┐
│                   nginx                             │
│   - Serve arquivos estáticos do frontend            │
│   - Proxy /api/* → Spring Boot :8080                │
└──────────┬──────────────────────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────┐
│            Spring Boot (Monolito Modular)           │
│  ┌─────────┐ ┌──────────┐ ┌────────┐ ┌──────────┐  │
│  │  auth   │ │   user   │ │ manga  │ │  reader  │  │
│  └─────────┘ └──────────┘ └────────┘ └──────────┘  │
│                  :8080                              │
└──────────┬──────────────────┬───────────────────────┘
           │                  │
┌──────────▼──────┐  ┌────────▼────────────────────┐
│   PostgreSQL    │  │   Google Cloud Storage      │
│   (metadados,  │  │   (arquivos de mangá,        │
│   usuários,    │  │    nomes ofuscados,           │
│   progresso)   │  │    URLs assinadas)            │
└─────────────────┘  └─────────────────────────────┘
```

**Comunicação assíncrona interna:** `@Async` (e-mails, processamento de upload)
**Jobs agendados:** `@Scheduled` (verificação de inatividade)
**Containerização:** Docker + Docker Compose
**Cloud:** GCP (Cloud Run ou GCE)

---

## 2. Estrutura de Módulos do Backend

```
backend/
└── src/main/java/com/buruna/
    ├── auth/
    │   ├── controller/     AuthController
    │   ├── service/        AuthService, TokenService
    │   ├── dto/            RegisterRequest, LoginRequest, TokenResponse
    │   └── security/       JwtFilter, SecurityConfig, RateLimitFilter
    │
    ├── user/
    │   ├── controller/     UserController, AdminUserController
    │   ├── service/        UserService, InactivityService (scheduler)
    │   ├── model/          User, Role (enum), Status (enum)
    │   └── dto/            UserResponse, UpdateRoleRequest, ApprovalRequest
    │
    ├── manga/
    │   ├── controller/     MangaController, VolumeController, PrivateMangaController
    │   ├── service/        MangaService, VolumeService, StorageService, DuplicateCheckService
    │   ├── model/          Manga, Volume, Tag, TagCategory, MangaTag
    │   └── dto/            MangaRequest, MangaResponse, VolumeRequest, VolumeResponse
    │
    ├── reader/
    │   ├── controller/     ReaderController
    │   ├── service/        ReaderService, ProgressService
    │   ├── model/          ReadingProgress, ReadingHistory, ReadingList, Rating
    │   └── dto/            ProgressRequest, HistoryResponse, ReadingListRequest
    │
    ├── admin/
    │   ├── controller/     DashboardController
    │   └── service/        DashboardService
    │
    ├── notification/
    │   └── service/        EmailService (@Async)
    │
    └── infra/
        ├── config/         GcsConfig, MailConfig, SecurityBeans
        ├── exception/      GlobalExceptionHandler
        └── storage/        GcsStorageClient
```

---

## 3. Contratos da API REST

> Base URL: `/api`
> Autenticação: `Authorization: Bearer <access_token>` (exceto rotas públicas)
> Roles: READER | COLLABORATOR | ADMIN

---

### 3.1 Auth

| Método | Rota             | Auth       | Descrição                              |
|--------|------------------|------------|----------------------------------------|
| POST   | `/auth/register` | ❌ público  | Solicitar cadastro                     |
| POST   | `/auth/login`    | ❌ público  | Login, retorna access + refresh token  |
| POST   | `/auth/refresh`  | ❌ público  | Renovar access token via refresh token |
| POST   | `/auth/logout`   | ✅ qualquer | Invalidar refresh token                |
| DELETE | `/auth/account`  | ✅ qualquer | Solicitar exclusão da própria conta    |

**POST /auth/register — body:**

```json
{
  "email": "string",
  "username": "string",
  "password": "string",
  "presentationMessage": "string",
  "avatarBase64": "string (opcional)"
}
```

**POST /auth/login — response:**

```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 3600
}
```

---

### 3.2 Usuários (Admin)

| Método | Rota                        | Auth  | Descrição                           |
|--------|-----------------------------|-------|-------------------------------------|
| GET    | `/admin/users`              | ADMIN | Listar todos os usuários (paginado) |
| GET    | `/admin/users/pending`      | ADMIN | Listar cadastros pendentes          |
| GET    | `/admin/users/{id}`         | ADMIN | Detalhes de um usuário              |
| PATCH  | `/admin/users/{id}/role`    | ADMIN | Alterar role                        |
| PATCH  | `/admin/users/{id}/status`  | ADMIN | Ativar/desativar conta              |
| PATCH  | `/admin/users/{id}/quota`   | ADMIN | Alterar cota de GB privado          |
| POST   | `/admin/users/{id}/approve` | ADMIN | Aprovar cadastro                    |
| POST   | `/admin/users/{id}/reject`  | ADMIN | Rejeitar cadastro (motivo opcional) |

**POST /admin/users/{id}/reject — body:**

```json
{
  "reason": "string (opcional)"
}
```

---

### 3.3 Mangás Públicos

| Método | Rota             | Auth          | Descrição                                |
|--------|------------------|---------------|------------------------------------------|
| GET    | `/mangas`        | ✅ qualquer    | Listar/buscar mangás públicos (paginado) |
| GET    | `/mangas/{slug}` | ✅ qualquer    | Detalhes de um mangá                     |
| POST   | `/mangas`        | COLLABORATOR+ | Criar novo mangá                         |
| PUT    | `/mangas/{id}`   | COLLABORATOR+ | Editar mangá (próprio ou admin)          |
| DELETE | `/mangas/{id}`   | COLLABORATOR+ | Deletar mangá (próprio ou admin)         |

**GET /mangas — query params:**

```
?page=0&size=20
&title=berserk          (busca por título ou título alternativo)
&tagIds=uuid1,uuid2     (filtro por tags)
&format=MANGA           (filtro por formato)
&status=COMPLETED       (filtro por status da obra)
```

**POST /mangas — body:**

```json
{
  "title": "string",
  "alternativeTitles": [
    "string"
  ],
  "synopsis": "string (opcional)",
  "coverBase64": "string (opcional)",
  "format": "MANGA | MANHWA | MANHUA | WEBTOON | ONESHOT",
  "originCountry": "string (opcional)",
  "statusOrigin": "ONGOING | COMPLETED | HIATUS | CANCELLED",
  "statusSite": "COMPLETE | INCOMPLETE",
  "year": 2025,
  "contentWarnings": [
    "NSFW",
    "GORE"
  ],
  "tagIds": [
    "uuid1",
    "uuid2"
  ]
}
```

---

### 3.4 Volumes

| Método | Rota                                   | Auth          | Descrição                              |
|--------|----------------------------------------|---------------|----------------------------------------|
| GET    | `/mangas/{mangaId}/volumes`            | ✅ qualquer    | Listar volumes de um mangá             |
| POST   | `/mangas/{mangaId}/volumes`            | COLLABORATOR+ | Upload de volume (multipart/form-data) |
| DELETE | `/mangas/{mangaId}/volumes/{volumeId}` | COLLABORATOR+ | Deletar volume                         |

**POST /mangas/{mangaId}/volumes — multipart:**

```
file: arquivo (PDF, EPUB, MOBI)
volumeNumber: integer
```

---

### 3.5 Coleção Privada

| Método | Rota                      | Auth          | Descrição                           |
|--------|---------------------------|---------------|-------------------------------------|
| GET    | `/my/mangas`              | ✅ qualquer    | Listar próprios mangás privados     |
| POST   | `/my/mangas`              | ✅ qualquer    | Upload de mangá privado (multipart) |
| PUT    | `/my/mangas/{id}`         | ✅ qualquer    | Editar mangá privado                |
| DELETE | `/my/mangas/{id}`         | ✅ qualquer    | Deletar mangá privado               |
| POST   | `/my/mangas/{id}/promote` | COLLABORATOR+ | Promover privado → público          |

**POST /my/mangas — multipart:**

```
file: arquivo (PDF, EPUB, MOBI)
title: string
volumeNumber: integer
```

---

### 3.6 Leitor

| Método | Rota                          | Auth       | Descrição                                  |
|--------|-------------------------------|------------|--------------------------------------------|
| GET    | `/reader/{volumeId}/url`      | ✅ qualquer | Obter URL assinada temporária do arquivo   |
| POST   | `/reader/{volumeId}/progress` | ✅ qualquer | Salvar progresso de leitura                |
| GET    | `/reader/progress/{mangaId}`  | ✅ qualquer | Obter progresso atual de um mangá          |
| GET    | `/reader/history`             | ✅ qualquer | Histórico de leitura do usuário (paginado) |

**POST /reader/{volumeId}/progress — body:**

```json
{
  "currentPage": 47
}
```

---

### 3.7 Lista de Leitura

| Método | Rota                      | Auth       | Descrição                      |
|--------|---------------------------|------------|--------------------------------|
| GET    | `/reading-list`           | ✅ qualquer | Listar todos os itens da lista |
| PUT    | `/reading-list/{mangaId}` | ✅ qualquer | Adicionar ou atualizar status  |
| DELETE | `/reading-list/{mangaId}` | ✅ qualquer | Remover da lista               |

**PUT /reading-list/{mangaId} — body:**

```json
{
  "status": "WANT_TO_READ | READING | COMPLETED | DROPPED"
}
```

---

### 3.8 Avaliação

| Método | Rota                  | Auth       | Descrição              |
|--------|-----------------------|------------|------------------------|
| POST   | `/mangas/{id}/rating` | ✅ qualquer | Avaliar (1–5 estrelas) |
| PUT    | `/mangas/{id}/rating` | ✅ qualquer | Atualizar avaliação    |
| DELETE | `/mangas/{id}/rating` | ✅ qualquer | Remover avaliação      |

**POST /mangas/{id}/rating — body:**

```json
{
  "score": 4
}
```

---

### 3.9 Tags

| Método | Rota              | Auth       | Descrição                                 |
|--------|-------------------|------------|-------------------------------------------|
| GET    | `/tags`           | ✅ qualquer | Listar todas as tags ativas por categoria |
| POST   | `/tags`           | ADMIN      | Criar nova tag                            |
| PUT    | `/tags/{id}`      | ADMIN      | Editar tag                                |
| DELETE | `/tags/{id}`      | ADMIN      | Soft delete de tag                        |
| GET    | `/tag-categories` | ✅ qualquer | Listar categorias                         |
| POST   | `/tag-categories` | ADMIN      | Criar categoria                           |

---

### 3.10 Dashboard

| Método | Rota               | Auth  | Descrição                           |
|--------|--------------------|-------|-------------------------------------|
| GET    | `/admin/dashboard` | ADMIN | Usuários ativos + storage utilizado |

**GET /admin/dashboard — response:**

```json
{
  "activeUsers": 8,
  "totalStorageUsedGb": 12.4,
  "storageByUser": [
    {
      "userId": "uuid",
      "username": "string",
      "usedGb": 1.2
    }
  ]
}
```

---

## 4. Modelo de Dados Final

```
User
├── id (UUID, PK)
├── email (VARCHAR, unique)
├── username (VARCHAR, unique)
├── password_hash (VARCHAR)          -- BCrypt
├── avatar_url (VARCHAR, nullable)
├── presentation_message (TEXT)
├── role (ENUM: READER/COLLABORATOR/ADMIN)
├── status (ENUM: PENDING/ACTIVE/INACTIVE)
├── quota_gb (DECIMAL)               -- cota de storage privado
├── last_access_at (TIMESTAMP)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)

Manga
├── id (UUID, PK)
├── slug (VARCHAR, unique)
├── title (VARCHAR)
├── alternative_titles (TEXT[])
├── synopsis (TEXT, nullable)
├── cover_url (VARCHAR, nullable)
├── format (ENUM)
├── origin_country (VARCHAR, nullable)
├── status_origin (ENUM)
├── status_site (ENUM)
├── year (INT, nullable)
├── content_warnings (TEXT[])
├── avg_rating (DECIMAL)             -- calculado
├── rating_count (INT)               -- calculado
├── view_count (INT)
├── is_public (BOOLEAN)
├── owner_id (UUID, FK → User)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)

Volume
├── id (UUID, PK)
├── manga_id (UUID, FK → Manga)
├── volume_number (INT)
├── file_url (VARCHAR)               -- nome ofuscado no GCP
├── file_hash (VARCHAR)              -- SHA-256 para duplicatas
├── file_size_bytes (BIGINT)
├── uploaded_by (UUID, FK → User)
└── created_at (TIMESTAMP)

TagCategory
├── id (UUID, PK)
├── name (VARCHAR, unique)
└── created_at (TIMESTAMP)

Tag
├── id (UUID, PK)
├── name (VARCHAR)
├── slug (VARCHAR, unique)
├── category_id (UUID, FK → TagCategory)
├── created_at (TIMESTAMP)
└── deleted_at (TIMESTAMP, nullable)   -- soft delete

MangaTag
├── manga_id (UUID, FK → Manga)
└── tag_id (UUID, FK → Tag)

ReadingProgress
├── id (UUID, PK)
├── user_id (UUID, FK → User)
├── volume_id (UUID, FK → Volume)
├── current_page (INT)
└── updated_at (TIMESTAMP)

ReadingHistory
├── id (UUID, PK)
├── user_id (UUID, FK → User)
├── volume_id (UUID, FK → Volume)
└── read_at (TIMESTAMP)

ReadingList
├── id (UUID, PK)
├── user_id (UUID, FK → User)
├── manga_id (UUID, FK → Manga)
├── status (ENUM: WANT_TO_READ/READING/COMPLETED/DROPPED)
└── updated_at (TIMESTAMP)

Rating
├── id (UUID, PK)
├── user_id (UUID, FK → User)
├── manga_id (UUID, FK → Manga)
├── score (INT, 1–5)
└── created_at (TIMESTAMP)

RefreshToken
├── id (UUID, PK)
├── token (VARCHAR, unique)
├── user_id (UUID, FK → User)
├── expires_at (TIMESTAMP)
└── created_at (TIMESTAMP)
```

---

## 5. Fluxos Críticos

### 5.1 Fluxo de Cadastro e Aprovação

```
1. Usuário preenche formulário (email, username, senha, foto, mensagem)
2. Backend: valida campos + aplica rate limit (rate limit de 5 req/hora por IP)
3. Backend: salva User com status=PENDING, senha com BCrypt
4. Backend: dispara e-mail assíncrono ao admin (@Async)
5. Admin acessa painel → visualiza cadastros pendentes
6. Admin aprova ou rejeita (motivo opcional)
7. Backend: atualiza status do User → ACTIVE ou mantém PENDING (rejeitado = deletar)
8. Backend: dispara e-mail ao usuário com resultado (@Async)
9. Usuário aprovado já pode fazer login
```

### 5.2 Fluxo de Upload de Volume Público

```
1. Colaborador/Admin seleciona arquivo no frontend
2. Frontend: envia multipart POST /mangas/{id}/volumes
3. Backend: valida role + tamanho do arquivo + formato
4. Backend: calcula SHA-256 do arquivo
5. Backend: verifica hash duplicado na tabela Volume
6. Se duplicado → retorna 409 Conflict
7. Backend: gera nome ofuscado para o arquivo
8. Backend: faz upload para o GCS (@Async se necessário)
9. Backend: persiste Volume no PostgreSQL com file_url ofuscado
10. Retorna 201 Created com dados do volume
```

### 5.3 Fluxo de Leitura

```
1. Usuário acessa página de um mangá → seleciona volume
2. Frontend: GET /reader/{volumeId}/url
3. Backend: valida autenticação + verifica acesso ao volume
4. Backend: solicita URL assinada ao GCS (expira em 30 min)
5. Backend: incrementa view_count do mangá
6. Frontend: carrega leitor inline com a URL assinada
7. Leitor: lazy loading — carrega apenas páginas visíveis
8. A cada virada de página: POST /reader/{volumeId}/progress
9. Backend: upsert em ReadingProgress + insere em ReadingHistory
```

### 5.4 Fluxo de Inatividade Automática

```
@Scheduled — roda diariamente às 02:00
1. Busca todos os Users com status=ACTIVE
2. Para cada usuário: verifica last_access_at
3. Se last_access_at < hoje - 75 dias (3 meses - 15 dias de aviso):
   → Envia e-mail de aviso (@Async)
4. Se last_access_at < hoje - 90 dias:
   → Atualiza User.status = INACTIVE
   → Deleta todos os Volumes privados (is_public=false) do usuário no GCS
   → Deleta registros dos Volumes privados no banco
```

---

## 6. Infraestrutura

### 6.1 Docker Compose

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: buruna
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}

  backend:
    build: ./backend
    depends_on: [ postgres ]
    environment:
      DB_URL, DB_USER, DB_PASSWORD
      JWT_SECRET, JWT_EXPIRATION
      REFRESH_TOKEN_EXPIRATION
      GCS_BUCKET_NAME, GCS_CREDENTIALS_PATH
      MAIL_USERNAME, MAIL_PASSWORD
      ADMIN_EMAIL

  frontend:
    build: ./frontend
    depends_on: [ backend ]

  nginx:
    image: nginx:alpine
    ports: [ "80:80" ]
    depends_on: [ frontend, backend ]
    volumes: [ ./nginx/nginx.conf:/etc/nginx/nginx.conf ]
```

### 6.2 nginx — Roteamento

```nginx
server {
    listen 80;

    location / {
      proxy_pass              http://frontend:80;
      proxy_set_header        Host $host;
      proxy_set_header        X-Real-IP $remote_addr;
      proxy_set_header        X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_intercept_errors  on;
      error_page              404 = @fallback;
    }

    location @fallback {
      rewrite ^ /index.html break;
      proxy_pass       http://frontend:80;
      proxy_set_header Host $host;
    }

    location /api/ {
      proxy_pass         http://backend:8080;
      proxy_set_header   Host $host;
      proxy_set_header   X-Real-IP $remote_addr;
      proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_read_timeout 120s;
    }
  }
```

### 6.3 Variáveis de Ambiente (.env)

```
# Banco
DB_URL=jdbc:postgresql://postgres:5432/buruna
DB_USER=
DB_PASSWORD=

# JWT
JWT_SECRET=
JWT_EXPIRATION=3600          # segundos (1h)
REFRESH_TOKEN_EXPIRATION=    # segundos (7 dias)

# GCP
GCS_BUCKET_NAME=
GCS_CREDENTIALS_PATH=

# E-mail (Gmail SMTP)
MAIL_USERNAME=
MAIL_PASSWORD=               # app password do Gmail
ADMIN_EMAIL=

# Upload
MAX_FILE_SIZE_MB=500

# Rate limit
RATE_LIMIT_REGISTER_PER_HOUR=5
```

### 6.4 Monitoramento de Downtime

- Solução: **UptimeRobot** (gratuito) — monitora a URL da aplicação e envia e-mail ao admin quando cair
- Sem necessidade de implementação custom no backend

---

## 7. Decisões Técnicas Registradas (ADRs)

| #  | Decisão                                                                           | Alternativa descartada                | Motivo                                                                                                                                                                                                                                                                                              |
|----|-----------------------------------------------------------------------------------|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Monolito modular                                                                  | Microsserviços                        | Complexidade desnecessária para projeto solo/pequeno                                                                                                                                                                                                                                                |
| 2  | Upload via backend                                                                | Upload direto GCS                     | Validações centralizadas (hash, tamanho, cota, formato)                                                                                                                                                                                                                                             |
| 3  | @Async/@Scheduled                                                                 | Kafka                                 | Over-engineering para volume de 10–100 usuários                                                                                                                                                                                                                                                     |
| 4  | nginx reverse proxy                                                               | API Gateway externo                   | Gratuito, já dominado, suficiente para o escopo                                                                                                                                                                                                                                                     |
| 5  | Offset/page                                                                       | Cursor-based                          | Volume de dados pequeno, simplicidade de implementação                                                                                                                                                                                                                                              |
| 6  | BCrypt                                                                            | SHA-256 puro                          | BCrypt é o padrão para senhas — adaptativo e seguro                                                                                                                                                                                                                                                 |
| 7  | GCS URLs assinadas                                                                | URLs públicas                         | Impede acesso a arquivos sem autenticação                                                                                                                                                                                                                                                           |
| 8  | UptimeRobot                                                                       | Implementação custom                  | Gratuito, zero manutenção, resolve o requisito                                                                                                                                                                                                                                                      |
| 9  | 7 índices explícitos (FKs críticas + file_hash + tag_id + ratings manga_id)       | Índice em toda FK e coluna filtrada   | Tabelas com <1000 linhas e colunas de baixa cardinalidade (ENUM, BOOLEAN) têm Seq Scan mais eficiente que manutenção de B-tree. Índices adicionados apenas onde há query específica de alta frequência ou tabela com crescimento ilimitado.                                                         |
| 10 | Rate limit no /auth/register sem captcha                                          | Rate limit + hCaptcha                 | Projeto solo com aprovação manual pelo admin. Captcha adiciona complexidade de integração externa sem ganho proporcional para 10–100 usuários. Rate limit de 5 req/hora por IP é suficiente para o MVP.                                                                                             |
| 11 | Listas (`alternative_titles`, `content_warnings`) como `TEXT` serializado em JSON | `TEXT[]` nativo do PostgreSQL         | `TEXT[]` exige type mapping customizado no Hibernate/JPA e não é portável. `TEXT` com `@Converter` via Jackson mantém a serialização no lado Java, é transparente para o ORM, e listas desses campos têm cardinalidade baixa (< 20 itens), sem necessidade de indexação individual dos elementos.   |
| 12 | Filtro de tags com semântica AND                                                  | Semântica OR (comportamento anterior) | OR retorna resultados demais e pouco relevantes. Selecionar "Ação" + "Completo" deve retornar obras que atendam ambos os critérios simultaneamente, não obras que tenham qualquer um deles. Implementado via subquery EXISTS por tagId para garantir que o mangá possua todas as tags selecionadas. |
| 13 | `GET /mangas/{slugOrId}` resolve UUID ou slug no mesmo endpoint                   | Rota separada `GET /mangas/id/{id}`   | Sem quebra de contrato com o frontend — slugs continuam funcionando, UUIDs passam a funcionar. Detecção via `UUID.fromString` com fallback para slug.                                                                                                                                               |
| 14 | `client_max_body_size 600M` no nginx + `proxy_request_buffering off`              | Limite padrão de 1MB                  | Backend já aceitava 500MB via `MAX_FILE_SIZE_MB`. Nginx era o gargalo. `proxy_request_buffering off` evita bufferização do upload em disco antes de encaminhar ao backend.                                                                                                                          |
| 15 | `TagSelector` com prop `excludeCategories` oculta "Aviso de Conteúdo"             | Remover a categoria do banco          | Avisos vivem em `content_warnings` na tabela `mangas`, não em `manga_tags`. Filtrar por `tagId` não os encontra. A categoria permanece no banco para gestão no painel admin — apenas a UI de seleção a oculta onde há campo dedicado de avisos.                                                     |
| 16  | Two-step fetch em `findPublic`: paginar sem `@EntityGraph`, depois batch load das tags por IDs | `@EntityGraph` + `Pageable` na mesma query | Hibernate não consegue paginar no SQL quando há fetch de coleção — aplica paginação em memória (HHH90003004). Separar em dois passos resolve sem perda de funcionalidade. |
| 17 | uq_volumes_file_hash removida em V15 — hash de volume não é globalmente único | Manter constraint | Usuários diferentes podem ter o mesmo arquivo na coleção privada. Hash ainda é persistido para detecção futura de corrupção. Unicidade de volume público é garantida pelo check no promote. |
| 18 | Promote valida título e hash apenas contra mangás públicos (isPublic=true) | Validar contra todos os mangás | O próprio mangá privado tem o mesmo título e hashes — validar globalmente bloquearia o promote do próprio dono. |