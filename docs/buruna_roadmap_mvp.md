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

- [ ] `V1__create_users_table.sql`
- [ ] `V2__create_refresh_tokens_table.sql`
- [ ] `V3__create_tag_categories_table.sql`
- [ ] `V4__create_tags_table.sql`
- [ ] `V5__create_mangas_table.sql`
- [ ] `V6__create_manga_tags_table.sql`
- [ ] `V7__create_volumes_table.sql`
- [ ] `V8__create_reading_progress_table.sql`
- [ ] `V9__create_reading_history_table.sql`
- [ ] `V10__create_reading_list_table.sql`
- [ ] `V11__create_ratings_table.sql`
- [ ] `V12__seed_tag_categories.sql` (categorias iniciais)
- [ ] `V13__seed_tags.sql` (lista inicial de tags)

---

## Fase 2 — Autenticação e Usuários (12% → 28%)

**Objetivo:** sistema de auth completo com aprovação de cadastro.

**Backend:**

- [ ] Entidade `User` + enums `Role` e `UserStatus`
- [ ] `POST /auth/register` — cadastro com BCrypt + envio de e-mail ao admin (@Async)
- [ ] `POST /auth/login` — JWT + Refresh Token
- [ ] `POST /auth/refresh` — renovar access token
- [ ] `POST /auth/logout` — invalidar refresh token
- [ ] `DELETE /auth/account` — exclusão de conta pelo usuário
- [ ] `GET /admin/users/pending` — listar cadastros pendentes
- [ ] `POST /admin/users/{id}/approve` — aprovar + e-mail ao usuário (@Async)
- [ ] `POST /admin/users/{id}/reject` — rejeitar + e-mail ao usuário (@Async)
- [ ] `GET /admin/users` — listar todos os usuários (paginado)
- [ ] `PATCH /admin/users/{id}/role` — alterar role
- [ ] `PATCH /admin/users/{id}/status` — ativar/desativar
- [ ] `PATCH /admin/users/{id}/quota` — configurar cota de GB
- [ ] Rate limit + Captcha no cadastro
- [ ] `SecurityConfig` com rotas públicas e protegidas
- [ ] `JwtFilter` na filter chain

**Frontend:**

- [ ] Tela de cadastro (email, username, senha, foto, mensagem)
- [ ] Tela de login
- [ ] Lógica de refresh token automático (interceptor Axios)
- [ ] Painel admin: lista de pendentes + aprovar/rejeitar
- [ ] Painel admin: lista de usuários + gestão de roles

---

## Fase 3 — Tags e Taxonomia (28% → 33%)

**Objetivo:** sistema de tags funcional para uso no cadastro de mangás.

**Backend:**

- [ ] Entidades `TagCategory` + `Tag`
- [ ] `GET /tags` — listar tags ativas por categoria
- [ ] `GET /tag-categories` — listar categorias
- [ ] `POST /tags` — criar tag (admin)
- [ ] `PUT /tags/{id}` — editar tag (admin)
- [ ] `DELETE /tags/{id}` — soft delete de tag (admin)
- [ ] `POST /tag-categories` — criar categoria (admin)

**Frontend:**

- [ ] Painel admin: gerenciar tags e categorias
- [ ] Componente de seleção de tags reutilizável (para uso no upload de mangás)

---

## Fase 4 — Biblioteca Pública: Mangás e Volumes (33% → 52%)

**Objetivo:** CRUD completo de mangás públicos com upload de arquivos.

**Backend:**

- [ ] Entidades `Manga` + `Volume` + `MangaTag`
- [ ] Integração com Google Cloud Storage (`GcsStorageClient`)
- [ ] Geração de nome ofuscado (UUID) para arquivos no GCS
- [ ] Cálculo de hash SHA-256 para detecção de duplicatas
- [ ] `POST /mangas` — criar mangá (colaborador/admin)
- [ ] `GET /mangas` — listar/buscar com filtros e paginação
- [ ] `GET /mangas/{slug}` — detalhes de um mangá
- [ ] `PUT /mangas/{id}` — editar mangá
- [ ] `DELETE /mangas/{id}` — deletar mangá + arquivo no GCS
- [ ] `POST /mangas/{id}/volumes` — upload de volume (multipart)
- [ ] `DELETE /mangas/{id}/volumes/{volumeId}` — deletar volume
- [ ] Validação de permissões por role (admin vs. colaborador)

**Frontend:**

- [ ] Tela de biblioteca (grade de capas, busca + filtros por tags)
- [ ] Tela de detalhes de um mangá (volumes, metadados)
- [ ] Formulário de upload de mangá + volumes
- [ ] Formulário de edição de mangá

---

## Fase 5 — Coleção Privada (52% → 62%)

**Objetivo:** upload e gestão de mangás privados com privacidade total.

**Backend:**

- [ ] `POST /my/mangas` — upload privado com validação de cota em GB
- [ ] `GET /my/mangas` — listar própria coleção privada
- [ ] `PUT /my/mangas/{id}` — editar mangá privado
- [ ] `DELETE /my/mangas/{id}` — deletar mangá privado + arquivo GCS
- [ ] `POST /my/mangas/{id}/promote` — promover privado → público (colaborador/admin)
- [ ] Validação de cota de GB por usuário

**Frontend:**

- [ ] Tela "Minha coleção" (mangás privados)
- [ ] Formulário de upload privado
- [ ] Indicador de cota utilizada/disponível
- [ ] Botão de promover para público (se permitido)

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
