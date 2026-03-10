# Burūna — Documento Inicial de Planejamento e Requisitos

> Documento gerado ao final das Etapas 1, 2 e 3 do ciclo de desenvolvimento.
> Projeto: Biblioteca pessoal de mangás com autenticação, upload e leitor web.

---

## 1. Planejamento

### 1.1 Stakeholders e Partes Interessadas

| Perfil        | Descrição                                                                       |
| ------------- | ------------------------------------------------------------------------------- |
| Administrador | Gerencia usuários, roles, tags, dashboard e biblioteca pública completa         |
| Colaborador   | Faz upload de mangás públicos, gerencia os próprios; herda permissões do Leitor |
| Leitor        | Lê mangás públicos, gerencia coleção privada; não herda permissões superiores   |

**Hierarquia de permissões:**

| Permissão                         | Leitor | Colaborador | Admin |
| --------------------------------- | ------ | ----------- | ----- |
| Ler mangás públicos               | ✅      | ✅           | ✅     |
| Mangás privados (cota em GB)      | ✅      | ✅           | ✅     |
| Upload de mangás públicos         | ❌      | ✅           | ✅     |
| Editar/deletar próprios públicos  | ❌      | ✅           | ✅     |
| Promover privado → público        | ❌      | ✅           | ✅     |
| Editar/deletar públicos de outros | ❌      | ❌           | ✅     |
| Gerenciar usuários/roles          | ❌      | ❌           | ✅     |
| Aprovar/rejeitar cadastros        | ❌      | ❌           | ✅     |
| Ver dashboard                     | ❌      | ❌           | ✅     |

**Usuários:** margem inicial de 10 pessoas, arquitetura preparada para até 100.

---

### 1.2 Viabilidade Técnica e Financeira

**Técnica:**

- Stack dominado pelo desenvolvedor: Java 21, Spring Boot, Spring Security, PostgreSQL, Docker, GCP, React

**Financeira:**

- Orçamento mensal: até R$50
- Estimativa de storage: ~30GB (100 volumes × 300MB) ≈ R$3/mês no GCP

---

### 1.3 Cronograma e Estratégia

- 
- **Estratégia:** MVP primeiro (auth + upload + leitura), features incrementais depois
- **Gestão de features:** GitHub Projects com kanban:
  - `Em desenvolvimento` / `Aprovadas` / `Sugestões em análise`

---

### 1.4 Riscos e Mitigações

| Risco                    | Mitigação                                                                       |
| ------------------------ | ------------------------------------------------------------------------------- |
| Acesso indevido          | Rate limit + Captcha no cadastro                                                |
| Abuso de storage         | Limite de GB por usuário (configurável pelo admin) + tamanho máximo por arquivo |
| Indisponibilidade        | Tolerável; alerta por e-mail ao admin quando a aplicação cair                   |
| Perda de dados           | Backup apenas dos dados de usuários (banco); arquivos de mangá confiados ao GCP |
| Duplicatas na biblioteca | Validação por hash do arquivo e/ou título antes da publicação                   |
| Exposição de arquivos    | URLs assinadas com expiração (Google Cloud Storage)                             |

---

### 1.5 Metodologia

- **Abordagem:** Informal, Kanban leve + ciclos curtos por feature
- **Documentação:** Pasta `./docs` no repositório (decisões, SRS, DDS, progresso)
- **Controle de versão:** Git + GitHub (monorepo)

---

## 2. Levantamento e Análise de Requisitos

### 2.1 Requisitos Funcionais

#### Módulo de Autenticação e Usuários

- Cadastro com: email, username, senha, foto de perfil, mensagem de apresentação
- Senhas armazenadas com **hash BCrypt** — nem o admin tem acesso às senhas
- Cadastro fica **pendente** até aprovação manual do admin
- Admin recebe **e-mail** a cada novo cadastro pendente
- Usuário recebe **e-mail** com resultado (aprovado/rejeitado); motivo de rejeição é opcional
- Login: email + senha (Google OAuth como feature futura — backlog)
- Sessão: **JWT + Refresh Token**
- Admin pode gerenciar roles de qualquer usuário a qualquer momento
- Usuário pode solicitar **exclusão da própria conta** (LGPD)
- **Inatividade:** 3 meses sem acesso → aviso por e-mail 15 dias antes → desativação automática
- Desativação (automática ou manual pelo admin) → deleção dos mangás privados do usuário
- Reativação de conta começa do zero (sem mangás privados)

#### Módulo de Mangás — Biblioteca Pública

- Formatos suportados: **PDF, EPUB, MOBI** (CBZ a avaliar futuramente)
- Upload passa pelo backend (validações centralizadas)
- Validação de **duplicatas** antes da publicação (hash do arquivo e/ou título)
- Obras podem ter **múltiplos volumes** agrupados
- Busca por título (incluindo títulos alternativos) + filtros por tags
- Paginação: offset/page
- **Permissões de edição/deleção:**
  - Admin: gerencia todos os mangás públicos
  - Colaborador: gerencia apenas seus próprios uploads
- Colaborador/Admin pode promover mangá privado → público

#### Módulo de Mangás — Coleção Privada

- Disponível para todos os roles (leitor, colaborador, admin)
- **Cota em GB** por usuário, configurável pelo admin (pode variar por usuário ou role)
- Invisibilidade total: nem quantidade, nem metadados, nem arquivos são visíveis para o admin
- Nome do arquivo **ofuscado/embaralhado** no servidor desde o upload
- Metadados mínimos armazenados (apenas o necessário para funcionamento)
- Mangá privado pode ser promovido a público por quem tem permissão de upload público

#### Módulo de Leitura

- Leitor **inline no browser**, construído do zero
- **Modo scroll vertical contínuo** (estilo webtoon)
- **Modo página a página** (estilo Kindle/mangá físico)
- Progresso salvo automaticamente: **página + capítulo + volume**, por usuário por obra
- **Histórico de leitura:** data + obra + volume lido, visível para o próprio usuário
- Modo escuro + ajustes de brilho/contraste
- **Gestos de swipe** (esquerda/direita) para virar página no mobile
- Controles do leitor **aparecem/desaparecem** ao toque/clique (experiência imersiva)
- **Lazy loading** de páginas (carrega apenas o que está visível)

#### Módulo de Engajamento

- **Lista de leitura** por usuário com status: `Quero ler` / `Lendo` / `Concluído` / `Dropei`
- **Avaliação** de mangás públicos pelos usuários (formato a definir)
- **Notificações de novos volumes:** backlog (e-mail + sino no site) — baixa prioridade no MVP

#### Módulo Admin — Dashboard

- Total de **usuários ativos** (contas não desativadas)
- **Storage utilizado** (total e por usuário)

---

### 2.2 Requisitos Não Funcionais

| Categoria       | Decisão                                                                                                |
| --------------- | ------------------------------------------------------------------------------------------------------ |
| Performance     | Lazy loading no leitor (carrega só páginas visíveis)                                                   |
| Segurança       | BCrypt (senhas) + JWT + Refresh Token + Rate limit + Captcha + URLs assinadas com expiração (GCP)      |
| Escalabilidade  | Suporte a 10 usuários no MVP, arquitetura preparada para 100                                           |
| Responsividade  | Desktop e mobile (tablet no backlog)                                                                   |
| Privacidade     | Exclusão de conta a pedido do usuário (LGPD); senhas com BCrypt; arquivos privados com nomes ofuscados |
| Disponibilidade | Tolerável; alerta por e-mail ao admin em caso de downtime                                              |
| Backup          | Apenas banco de dados de usuários                                                                      |

---

### 2.3 User Stories

**Autenticação**

- Como visitante, quero me cadastrar com email, username, senha, foto e mensagem para solicitar acesso
- Como usuário pendente, quero receber e-mail informando se fui aprovado ou rejeitado
- Como usuário aprovado, quero fazer login com email e senha com sessão mantida via refresh token
- Como usuário, quero solicitar a exclusão da minha conta

**Admin**

- Como admin, quero receber e-mail quando houver novos cadastros pendentes
- Como admin, quero aprovar ou rejeitar cadastros com motivo opcional
- Como admin, quero alterar o role de qualquer usuário
- Como admin, quero ver dashboard com usuários ativos e storage utilizado
- Como admin, quero gerenciar a lista de tags (criar, editar, soft delete)
- Como admin, quero configurar a cota de GB de mangás privados por usuário/role

**Biblioteca e Upload**

- Como colaborador/admin, quero fazer upload de mangás públicos com metadados e tags
- Como colaborador/admin, quero editar e deletar meus próprios mangás públicos
- Como admin, quero editar e deletar qualquer mangá público
- Como qualquer usuário, quero fazer upload de mangás privados (até minha cota em GB)
- Como colaborador/admin, quero promover um mangá privado para público
- Como qualquer usuário, quero buscar mangás por título e filtrar por tags
- Como qualquer usuário, quero organizar minha lista de leitura com status
- Como qualquer usuário, quero avaliar mangás públicos

**Leitura**

- Como leitor, quero ler mangás inline no browser com modo scroll ou página a página
- Como leitor, quero que meu progresso (página, capítulo, volume) seja salvo automaticamente
- Como leitor, quero ver meu histórico de leitura
- Como leitor, quero alternar entre modo claro e escuro e ajustar brilho/contraste
- Como leitor mobile, quero navegar entre páginas com swipe

---

## 3. Design e Arquitetura

### 3.1 Arquitetura

- **Estilo:** Monolito modular
- **Módulos:** `auth`, `user`, `manga`, `reader`
- **Repositório:** Monorepo (frontend + backend)
- **Proxy:** nginx como reverse proxy (serve frontend estático + roteia `/api/*` para o Spring Boot)
- **Jobs agendados:** `@Scheduled` do Spring Boot (verificação de inatividade, etc.)
- **Operações assíncronas:** `@Async` do Spring Boot (e-mails, processamento de upload)
- **Sem Kafka**, sem API Gateway externo

### 3.2 Stack Tecnológico

| Camada              | Tecnologia                                     |
| ------------------- | ---------------------------------------------- |
| Backend             | Java 21 + Spring Boot                          |
| Segurança           | Spring Security + JWT + Refresh Token + BCrypt |
| Banco de dados      | PostgreSQL + Flyway                            |
| Storage de arquivos | Google Cloud Storage                           |
| E-mail              | Gmail SMTP + Spring Mail                       |
| Frontend            | React + shadcn/ui                              |
| Leitor de mangás    | Construído do zero                             |
| Proxy reverso       | nginx                                          |
| Containerização     | Docker + Docker Compose                        |
| Cloud               | GCP (Cloud Run ou GCE)                         |
| Versionamento       | Git + GitHub                                   |

### 3.3 Design da API

- **Sem versionamento** de rotas no MVP
- **Upload:** arquivos enviados pelo frontend → backend → GCP (validações centralizadas no servidor)
- **Paginação:** offset/page
- **Documentação:** API consumida apenas pelo próprio frontend (sem Swagger no MVP)
- **URLs de leitura:** assinadas com expiração via Google Cloud Storage

### 3.4 Modelagem de Dados

#### Entidade: User

| Campo                | Tipo      | Notas                                            |
| -------------------- | --------- | ------------------------------------------------ |
| id                   | UUID      | PK                                               |
| email                | VARCHAR   | único, obrigatório                               |
| username             | VARCHAR   | único, obrigatório                               |
| password_hash        | VARCHAR   | BCrypt, obrigatório                              |
| avatar_url           | VARCHAR   | opcional                                         |
| presentation_message | TEXT      | obrigatório no cadastro                          |
| role                 | ENUM      | READER, COLLABORATOR, ADMIN                      |
| status               | ENUM      | PENDING, ACTIVE, INACTIVE                        |
| quota_gb             | DECIMAL   | cota de storage privado, configurável pelo admin |
| last_access_at       | TIMESTAMP | base para inatividade                            |
| created_at           | TIMESTAMP |                                                  |
| updated_at           | TIMESTAMP |                                                  |

#### Entidade: Manga (obra)

| Campo              | Tipo      | Notas                                    |
| ------------------ | --------- | ---------------------------------------- |
| id                 | UUID      | PK                                       |
| slug               | VARCHAR   | único, URL amigável ex: `berserk`        |
| title              | VARCHAR   | obrigatório                              |
| alternative_titles | TEXT[]    | lista de títulos alternativos para busca |
| synopsis           | TEXT      | opcional                                 |
| cover_url          | VARCHAR   | opcional                                 |
| format             | ENUM      | MANGA, MANHWA, MANHUA, WEBTOON, ONE_SHOT |
| origin_country     | VARCHAR   | ex: Japan, Korea, China                  |
| status_origin      | ENUM      | ONGOING, COMPLETED, HIATUS, CANCELLED    |
| status_site        | ENUM      | COMPLETE, INCOMPLETE                     |
| year               | INT       | ano de lançamento                        |
| content_warnings   | TEXT[]    | ex: NSFW, gore, triggers                 |
| avg_rating         | DECIMAL   | calculado automaticamente                |
| rating_count       | INT       | quantidade de votos                      |
| view_count         | INT       | contador de leituras/visualizações       |
| is_public          | BOOLEAN   | público ou privado                       |
| owner_id           | UUID      | FK → User                                |
| created_at         | TIMESTAMP |                                          |
| updated_at         | TIMESTAMP |                                          |

#### Entidade: Volume

| Campo           | Tipo      | Notas                       |
| --------------- | --------- | --------------------------- |
| id              | UUID      | PK                          |
| manga_id        | UUID      | FK → Manga                  |
| volume_number   | INT       | obrigatório                 |
| file_url        | VARCHAR   | nome ofuscado no GCP        |
| file_hash       | VARCHAR   | para detecção de duplicatas |
| file_size_bytes | BIGINT    | para controle de cota       |
| uploaded_by     | UUID      | FK → User                   |
| created_at      | TIMESTAMP |                             |

#### Entidades de Taxonomia (Tags)

| Entidade    | Campos principais                | Notas                                        |
| ----------- | -------------------------------- | -------------------------------------------- |
| TagCategory | id, name, createdAt              | ex: Gênero, Tema, Autor, Artista, Demografia |
| Tag         | id, name, category_id, deletedAt | soft delete                                  |
| MangaTag    | manga_id, tag_id                 | relação N:N                                  |

#### Entidades de Leitura e Engajamento

| Entidade        | Campos principais                                                            |
| --------------- | ---------------------------------------------------------------------------- |
| ReadingProgress | id, user_id, volume_id, current_page, updated_at                             |
| ReadingHistory  | id, user_id, volume_id, read_at                                              |
| ReadingList     | id, user_id, manga_id, status (WANT_TO_READ / READING / COMPLETED / DROPPED) |
| Rating          | id, user_id, manga_id, score, created_at                                     |

---

### 3.5 UI/UX

- **Tema padrão:** Dark mode (usuário pode alternar)
- **Biblioteca:** Grade de capas estilo Netflix/MangaDex
- **Navegação mobile:** Barra inferior estilo app
- **Controles do leitor:** Aparecem/desaparecem ao toque/clique (experiência imersiva)
- **Responsivo:** Desktop e mobile

---

## Backlog (Features Futuras)

- Login social com Google (OAuth)
- Notificações de novos volumes (e-mail + sino no site)
- Suporte a formato CBZ/CBR
- Suporte a tablet
- Swagger/OpenAPI
- Versionamento de API - Fora de necessidade total no momento
- Integração com APIs externas (MyAnimeList ID, Anilist ID) - Em Análise
- Captcha no cadastro (hCaptcha ou Google reCAPTCHA v3)
- Notificar todos os usuários com role ADMIN ao invés de um e-mail fixo (`ADMIN_EMAIL`)
- Email profissional para notificações

## Qualidade/Corretude
- N+1 em findPublic — @EntityGraph no repository resolve
- DataIntegrityViolationException → 500 — mapear no GlobalExceptionHandler para 409
- RateLimitFilter sem limpeza — leak de memória gradual
- VolumeService.upload carrega arquivo inteiro em heap — SHA-256 via stream
- toResponse package-private — mudar para private

## Baixa prioridade
- Filtro de tags OR vs AND — nova ADR
- file_hash sem UNIQUE constraint — adicionar migration
- Signed URL no GCS com ADC — verificar quando for fazer **DEPLOY - Phase 9**
- filterByTitle não busca alternativeTitles — limitação conhecida
- Inconsistência de idioma nas mensagens — normalizar antes do **DEPLOY - Phase 9**
- JwtFilter com query por request — irrelevante no volume atual

---

## Estrutura do Repositório

```
/
├── backend/          # Spring Boot
├── frontend/         # React + shadcn/ui
├── nginx/            # Configuração do proxy
├── docker-compose.yml
└── docs/
    ├── planning.md       # Este documento
    ├── srs.md            # Software Requirements Specification
    ├── dds.md            # Design Document Specification
    └── decisions/        # ADRs (Architecture Decision Records)
```
