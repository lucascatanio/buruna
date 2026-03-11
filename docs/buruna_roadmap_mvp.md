# Burūna — Roadmap MVP (v1.0)

---

## Fase 0 — Setup e Fundação (0% → 5%)

**Objetivo:** repositório, estrutura de projeto e infra local funcionando.

- [x] Criar monorepo no GitHub
- [x] Criar estrutura de pastas: `/backend`, `/frontend`, `/nginx`, `/docs`
- [x] Configurar `docker-compose.yml` com PostgreSQL + backend + frontend + nginx
- [x] Criar projeto Spring Boot com dependências base (Web, Security, JPA, Flyway, Mail, Lombok)
- [x] Criar projeto React com Vite + TypeScript + shadcn/ui + Tailwind + React Router
- [x] Configurar arquivo `.env` e `.env.example`
- [x] Criar `GlobalExceptionHandler` com padrão de resposta de erro
- [x] Configurar Flyway + primeira migration vazia (`V1__init.sql`)
- [ ] Configurar GitHub Projects com kanban (Em desenvolvimento / Aprovadas / Sugestões)
- [x] README inicial do projeto

---

## Fase 1 — Banco de Dados: Schema Base (5% → 12%)

**Objetivo:** todas as tabelas do MVP criadas e versionadas.

- [x] `V1__create_users_table.sql`
- [x] `V2__create_refresh_tokens_table.sql`
- [x] `V3__create_tag_categories_table.sql`
- [x] `V4__create_tags_table.sql`
- [x] `V5__create_mangas_table.sql`
- [x] `V6__create_manga_tags_table.sql`
- [x] `V7__create_volumes_table.sql`
- [x] `V8__create_reading_progress_table.sql`
- [x] `V9__create_reading_history_table.sql`
- [x] `V10__create_reading_list_table.sql`
- [x] `V11__create_ratings_table.sql`
- [x] `V12__seed_tag_categories.sql` (categorias iniciais)
- [x] `V13__seed_tags.sql` (lista inicial de tags)

---

## Fase 2 — Autenticação e Usuários (12% → 28%)

**Objetivo:** sistema de auth completo com aprovação de cadastro.

**Backend:**

- [x] Entidade `User` + enums `Role` e `UserStatus`
- [x] `POST /auth/register` — cadastro com BCrypt + envio de e-mail ao admin (@Async)
- [x] `POST /auth/login` — JWT + Refresh Token
- [x] `POST /auth/refresh` — renovar access token
- [x] `POST /auth/logout` — invalidar refresh token
- [x] `DELETE /auth/account` — exclusão de conta pelo usuário
- [x] `GET /admin/users/pending` — listar cadastros pendentes
- [x] `POST /admin/users/{id}/approve` — aprovar + e-mail ao usuário (@Async)
- [x] `POST /admin/users/{id}/reject` — rejeitar + e-mail ao usuário (@Async)
- [x] `GET /admin/users` — listar todos os usuários (paginado)
- [x] `PATCH /admin/users/{id}/role` — alterar role
- [x] `PATCH /admin/users/{id}/status` — ativar/desativar
- [x] `PATCH /admin/users/{id}/quota` — configurar cota de GB
- [x] ~~Rate limit + Captcha no cadastro~~ — Captcha movido para backlog; rate limit implementado via `RateLimitFilter`
- [x] `SecurityConfig` com rotas públicas e protegidas
- [x] `JwtFilter` na filter chain

**Frontend:**

- [x] Tela de cadastro (email, username, senha, foto, mensagem)
- [x] Tela de login
- [x] Lógica de refresh token automático (interceptor Axios)
- [x] Painel admin: lista de pendentes + aprovar/rejeitar
- [x] Painel admin: lista de usuários + gestão de roles

---

## Fase 3 — Tags e Taxonomia (28% → 33%)

**Objetivo:** sistema de tags funcional para uso no cadastro de mangás.

**Backend:**

- [x] Entidades `TagCategory` + `Tag`
- [x] `GET /tags` — listar tags ativas por categoria
- [x] `GET /tag-categories` — listar categorias
- [x] `POST /tags` — criar tag (admin)
- [x] `PUT /tags/{id}` — editar tag (admin)
- [x] `DELETE /tags/{id}` — soft delete de tag (admin)
- [x] `POST /tag-categories` — criar categoria (admin)

**Frontend:**

- [x] Painel admin: gerenciar tags e categorias
- [x] Componente de seleção de tags reutilizável (para uso no upload de mangás)

---

## Fase 4 — Biblioteca Pública: Mangás e Volumes (33% → 52%)

**Objetivo:** CRUD completo de mangás públicos com upload de arquivos.

**Backend:**

- [x] Entidades `Manga` + `Volume` + `MangaTag`
- [x] Integração com Google Cloud Storage (`GcsStorageClient`)
- [x] Geração de nome ofuscado (UUID) para arquivos no GCS
- [x] Cálculo de hash SHA-256 para detecção de duplicatas
- [x] `POST /mangas` — criar mangá (colaborador/admin)
- [x] `GET /mangas` — listar/buscar com filtros e paginação
- [x] `GET /mangas/{slug}` — detalhes de um mangá
- [x] `PUT /mangas/{id}` — editar mangá
- [x] `DELETE /mangas/{id}` — deletar mangá + arquivo no GCS
- [x] `POST /mangas/{id}/volumes` — upload de volume (multipart)
- [x] `DELETE /mangas/{id}/volumes/{volumeId}` — deletar volume
- [x] Validação de permissões por role (admin vs. colaborador)

**Frontend:**

- [x] Tela de biblioteca (grade de capas, busca + filtros por tags)
- [x] Tela de detalhes de um mangá (volumes, metadados)
- [x] Formulário de upload de mangá + volumes
- [x] Formulário de edição de mangá

---

## Fase 5 — Coleção Privada (52% → 62%)

**Objetivo:** upload e gestão de mangás privados com privacidade total.

**Backend:**

- [x] `POST /my/mangas` — upload privado com validação de cota em GB
- [x] `GET /my/mangas` — listar própria coleção privada
- [x] `PUT /my/mangas/{id}` — editar mangá privado
- [x] `DELETE /my/mangas/{id}` — deletar mangá privado + arquivo GCS
- [x] `POST /my/mangas/{id}/promote` — promover privado → público (colaborador/admin)
- [x] Validação de cota de GB por usuário

**Frontend:**

- [x] Tela "Minha coleção" (mangás privados)
- [x] Formulário de upload privado
- [x] Indicador de cota utilizada/disponível
- [x] Botão de promover para público (se permitido)

---

## Fase 6 — Leitor (62% → 78%)

**Objetivo:** experiência de leitura completa e confortável.

**Backend:**

- [ ] `GET /reader/{volumeId}/url` — gerar URL assinada GCS (expira em 30 min) + incrementar view_count
- [ ] `POST /reader/{volumeId}/progress` — salvar progresso (upsert)
- [ ] `GET /reader/progress/{mangaId}` — recuperar progresso atual
- [ ] `GET /reader/history` — histórico de leitura (paginado)

**Frontend:**

- [ ] Leitor inline com modo **página a página**
- [ ] Leitor inline com modo **scroll vertical contínuo**
- [ ] Alternância entre modos de leitura
- [ ] Controles que aparecem/desaparecem ao toque/clique
- [ ] Dark mode + ajuste de brilho/contraste
- [ ] Salvar progresso automaticamente a cada virada de página
- [ ] Retomar leitura do ponto salvo
- [ ] Lazy loading de páginas
- [ ] Swipe esquerda/direita no mobile
- [ ] Tela de histórico de leitura do usuário

---

## Fase 7 — Engajamento (78% → 87%)

**Objetivo:** lista de leitura e avaliações.

**Backend:**

- [ ] `PUT /reading-list/{mangaId}` — adicionar/atualizar status
- [ ] `GET /reading-list` — listar lista de leitura do usuário
- [ ] `DELETE /reading-list/{mangaId}` — remover da lista
- [ ] `POST /mangas/{id}/rating` — avaliar (1–5 estrelas)
- [ ] `PUT /mangas/{id}/rating` — atualizar avaliação
- [ ] `DELETE /mangas/{id}/rating` — remover avaliação
- [ ] Recalcular `avg_rating` e `rating_count` automaticamente

**Frontend:**

- [ ] Componente de lista de leitura (Quero ler / Lendo / Concluído / Dropei)
- [ ] Componente de avaliação por estrelas (1–5)
- [ ] Exibir rating médio na tela de detalhes do mangá

---

## Fase 8 — Dashboard Admin e Jobs (87% → 93%)

**Objetivo:** painel administrativo e automações de manutenção.

**Backend:**

- [ ] `GET /admin/dashboard` — usuários ativos + storage utilizado (total e por usuário)
- [ ] `@Scheduled` — job diário de verificação de inatividade (aviso 15 dias + desativação 90 dias)
- [ ] Job: deleção automática de mangás privados de usuários desativados no GCS + banco

**Frontend:**

- [ ] Tela de dashboard admin (usuários ativos + storage)

---

## Fase 9 — Infra, Deploy e Polimento (93% → 100%)

**Objetivo:** aplicação no ar com segurança e monitoramento.

- [ ] Dockerfiles multi-stage para backend e frontend
- [ ] Docker Compose de produção com health checks
- [ ] Configuração nginx completa (HTTPS, proxy, static files)
- [ ] Deploy no GCP (Cloud Run ou GCE)
- [ ] Configurar UptimeRobot para monitoramento + alerta de downtime por e-mail
- [ ] Configurar Gmail App Password para envio de e-mails em produção
- [ ] Variáveis de ambiente configuradas no GCP
- [ ] Testes dos fluxos críticos em produção (cadastro, upload, leitura)
- [ ] Responsividade mobile revisada em dispositivo real
- [ ] README final com instruções de setup e deploy

---

## Resumo por Fase

| Fase | Descrição               | Progresso  |
| ---- | ----------------------- | ---------- |
| 0    | Setup e fundação        | 0% → 5%    |
| 1    | Schema do banco         | 5% → 12%   |
| 2    | Autenticação e usuários | 12% → 28%  |
| 3    | Tags e taxonomia        | 28% → 33%  |
| 4    | Biblioteca pública      | 33% → 52%  |
| 5    | Coleção privada         | 52% → 62%  |
| 6    | Leitor                  | 62% → 78%  |
| 7    | Engajamento             | 78% → 87%  |
| 8    | Dashboard e jobs        | 87% → 93%  |
| 9    | Infra e deploy          | 93% → 100% |

---