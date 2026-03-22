# Burūna — Roadmap MVP (v1.0)

---

## Fase 0 — Setup e fundação (0% → 5%)

**Objetivo:** repositório, estrutura de projeto e infra local rodando.

- [x] Criar monorepo no GitHub
- [x] Criar estrutura de pastas: `/backend`, `/frontend`, `/nginx`, `/docs`
- [x] Configurar `docker-compose.yml` com PostgreSQL + backend + frontend + nginx
- [x] Criar projeto Spring Boot com dependências base (Web, Security, JPA, Flyway, Mail, Lombok)
- [x] Criar projeto React com Vite + TypeScript + shadcn/ui + Tailwind + React Router
- [x] Configurar arquivo `.env` e `.env.example`
- [x] Criar `GlobalExceptionHandler` com padrão de resposta de erro
- [x] Configurar Flyway + primeira migration vazia (`V1__init.sql`)
- [x] Configurar GitHub Projects com kanban (Em desenvolvimento / Aprovadas / Sugestões)
- [x] README inicial do projeto

---

## Fase 1 — Banco de dados: schema base (5% → 12%)

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

## Fase 2 — Autenticação e usuários (12% → 28%)

**Objetivo:** auth completo com aprovação de cadastro funcionando ponta a ponta.

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
- [x] ~~Rate limit + Captcha no cadastro~~ — Captcha movido pro backlog; rate limit implementado via `RateLimitFilter`
- [x] `SecurityConfig` com rotas públicas e protegidas
- [x] `JwtFilter` na filter chain

**Frontend:**

- [x] Tela de cadastro (email, username, senha, foto, mensagem)
- [x] Tela de login
- [x] Lógica de refresh token automático (interceptor Axios)
- [x] Painel admin: lista de pendentes + aprovar/rejeitar
- [x] Painel admin: lista de usuários + gestão de roles

---

## Fase 3 — Tags e taxonomia (28% → 33%)

**Objetivo:** tags funcionando pra uso no cadastro de mangás.

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
- [x] Componente de seleção de tags reutilizável (pra uso no upload de mangás)

---

## Fase 4 — Biblioteca pública: mangás e volumes (33% → 52%)

**Objetivo:** CRUD de mangás públicos com upload de arquivos.

**Backend:**

- [x] Entidades `Manga` + `Volume` + `MangaTag`
- [x] Integração com Google Cloud Storage (`GcsStorageClient`)
- [x] Geração de nome ofuscado (UUID) pra arquivos no GCS
- [x] Detecção de duplicatas via MD5 dos metadados do GCS (ver ADR 24)
- [x] `POST /mangas` — criar mangá (colaborador/admin)
- [x] `GET /mangas` — listar/buscar com filtros e paginação
- [x] `GET /mangas/{slug}` — detalhes de um mangá
- [x] `PUT /mangas/{id}` — editar mangá
- [x] `DELETE /mangas/{id}` — deletar mangá + arquivo no GCS
- [x] ~~`POST /mangas/{id}/volumes` — upload de volume (multipart/form-data)~~ — substituído por fluxo de duas fases via Signed URL (ver ADR 23)
- [x] `POST /mangas/{id}/volumes/upload-url` — gerar URL assinada de PUT no GCS (COLLABORATOR+)
- [x] `POST /mangas/{id}/volumes/finalize` — persistir volume após upload direto ao GCS (COLLABORATOR+)
- [x] `DELETE /mangas/{id}/volumes/{volumeId}` — deletar volume
- [x] Validação de permissões por role (admin vs. colaborador)

**Frontend:**

- [x] Tela de biblioteca (grade de capas, busca + filtros por tags)
- [x] Tela de detalhes de um mangá (volumes, metadados)
- [x] Formulário de upload de mangá + volumes
- [x] Formulário de edição de mangá

---

## Fase 5 — Coleção privada (52% → 62%)

**Objetivo:** upload e gestão de mangás privados, isolados por usuário.

**Backend:**

- [x] `POST /my/mangas` — criar mangá privado (JSON, sem arquivo)
- [x] `POST /my/mangas/{id}/volumes/upload-url` — gerar URL assinada de PUT pra volume
- [x] `POST /my/mangas/{id}/volumes/finalize` — persistir volume após upload direto ao GCS
- [x] `GET /my/mangas` — listar própria coleção privada (paginado)
- [x] `GET /my/mangas/{id}` — detalhes de mangá privado
- [x] `GET /my/mangas/quota` — consultar cota utilizada/disponível
- [x] `PUT /my/mangas/{id}` — editar mangá privado
- [x] `DELETE /my/mangas/{id}` — deletar mangá privado + arquivo GCS
- [x] `DELETE /my/mangas/{id}/volumes/{volumeId}` — deletar volume privado
- [x] `POST /my/mangas/{id}/promote` — promover privado → público (colaborador/admin)
- [x] Validação de cota de GB por usuário

**Frontend:**

- [x] Tela "Minha coleção" (mangás privados)
- [x] Formulário de upload privado
- [x] Indicador de cota utilizada/disponível
- [x] Botão de promover pra público (se permitido)

---

## Fase 6 — Leitor (62% → 78%)

**Objetivo:** leitura funcional e confortável no browser.

**Backend:**

- [x] `GET /reader/{volumeId}/url` — gerar URL assinada GCS (expira em 30 min) + incrementar view_count
- [x] `POST /reader/{volumeId}/progress` — salvar progresso (upsert)
- [x] `GET /reader/{volumeId}/progress` — recuperar progresso de volume específico
- [x] `GET /reader/progress/{mangaId}` — recuperar progresso mais recente do mangá
- [x] `GET /reader/progress/batch` — recuperar progresso de múltiplos volumes em lote
- [x] `GET /reader/history` — histórico de leitura (paginado)

**Frontend:**

- [x] Leitor inline com modo página a página
- [x] Leitor inline com modo scroll vertical contínuo
- [x] Alternância entre modos de leitura
- [x] Controles que aparecem/desaparecem ao toque/clique
- [x] Dark mode + ajuste de brilho/contraste
- [x] Salvar progresso automaticamente a cada virada de página
- [x] Retomar leitura do ponto salvo
- [x] Lazy loading de páginas
- [x] Swipe esquerda/direita no mobile
- [x] Tela de histórico de leitura do usuário

---

## Fase 7 — Engajamento (78% → 87%)

**Objetivo:** lista de leitura e avaliações.

**Backend:**

- [x] `PUT /reading-list/{mangaId}` — adicionar/atualizar status
- [x] `GET /reading-list` — listar lista de leitura do usuário
- [x] `DELETE /reading-list/{mangaId}` — remover da lista
- [x] `POST /mangas/{id}/rating` — avaliar (1–5 estrelas)
- [x] `PUT /mangas/{id}/rating` — atualizar avaliação
- [x] `DELETE /mangas/{id}/rating` — remover avaliação
- [x] Recalcular `avg_rating` e `rating_count` automaticamente

**Frontend:**

- [x] Componente de lista de leitura (Quero ler / Lendo / Concluído / Dropei)
- [x] Componente de avaliação por estrelas (1–5)
- [x] Exibir rating médio na tela de detalhes do mangá

---

## Fase 8 — Dashboard admin e jobs (87% → 93%)

**Objetivo:** painel administrativo e automações de manutenção.

**Backend:**

- [x] `GET /admin/dashboard` — usuários ativos + storage utilizado (total e por usuário)
- [x] `@Scheduled` — job diário de verificação de inatividade (aviso aos 75 dias + desativação aos 90 dias)
- [x] Job: deleção automática de mangás privados de usuários desativados no GCS + banco
- [x] `POST /admin/jobs/inactivity` — trigger manual do InactivityJob (header `X-Job-Secret`)

**Frontend:**

- [x] Tela de dashboard admin (usuários ativos + storage)

---

## Fase 9 — Infra, deploy e polimento (93% → 100%)

**Objetivo:** aplicação no ar, segura e monitorada.

- [x] Dockerfiles multi-stage pra backend e frontend
- [x] ~~Docker Compose de produção com health checks~~ — substituído por Cloud Run (serviços stateless gerenciados pelo GCP)
- [x] Configuração nginx no frontend (proxy `/api/*`, SPA fallback pra client-side routing)
- [x] Deploy no Cloud Run — backend e frontend como serviços independentes (us-east1)
- [x] PostgreSQL no GCE e2-micro com Docker (free tier permanente, us-east1-b)
- [x] VPC connector pra comunicação interna Cloud Run → GCE
- [x] Artifact Registry pra armazenar imagens Docker (us-east1)
- [x] Secret Manager pra variáveis sensíveis de produção
- [x] Cloud Scheduler pro InactivityJob (diário às 02:00)
- [x] CORS configurado no GCS pra leitura de PDFs pelo browser (`gcs-cors.json`)
- [x] Domínio `buruna.com.br` mapeado com TLS automático
- [x] UptimeRobot configurado pra monitoramento + alerta de downtime por e-mail
- [x] Gmail App Password configurado pra envio de e-mails em produção
- [x] Variáveis de ambiente via Secret Manager
- [x] Testes dos fluxos críticos em produção (cadastro, upload, leitura)
- [x] Responsividade mobile revisada em dispositivo real
- [x] README final com instruções de setup e deploy

---

## Resumo por fase

| Fase | Descrição               | Progresso  | Status   |
| ---- | ----------------------- | ---------- | -------- |
| 0    | Setup e fundação        | 0% → 5%    | Completo |
| 1    | Schema do banco         | 5% → 12%   | Completo |
| 2    | Autenticação e usuários | 12% → 28%  | Completo |
| 3    | Tags e taxonomia        | 28% → 33%  | Completo |
| 4    | Biblioteca pública      | 33% → 52%  | Completo |
| 5    | Coleção privada         | 52% → 62%  | Completo |
| 6    | Leitor                  | 62% → 78%  | Completo |
| 7    | Engajamento             | 78% → 87%  | Completo |
| 8    | Dashboard e jobs        | 87% → 93%  | Completo |
| 9    | Infra e deploy          | 93% → 100% | Completo |

**Projeto em produção: https://buruna.com.br**

---

## Backlog

Coisas que foram identificadas durante o desenvolvimento pra resolver depois:

- [ ] Upload direto GCS: lifecycle rule de 24h pra excluir arquivos órfãos (upload sem finalize)
- [ ] Migração de e-mail pra Resend + `@buruna.com.br` (DKIM/SPF/DMARC)
- [x] CORS do backend: adicionar `https://buruna.com.br` quando domínio propagar
- [ ] Compressão de PDF no upload
- [x] Cache de URL assinada no frontend (evitar gerar de novo antes dos 30 min)
- [x] `filterByTitle` não busca em `alternativeTitles`
- [ ] Testes de integração nos fluxos críticos (GitHub Actions @SpringBootTest)
- [x] Paginação no `InactivityJob` (escalar além de 100 usuários)
- [ ] hCaptcha no cadastro (movido do MVP)
- [ ] Notificações de novos volumes (e-mail + sino no site)
- [ ] Login social com Google (OAuth)
- [ ] Suporte a CBZ/CBR
- [ ] Integração com MyAnimeList / Anilist - em estudo
- [x] Swagger/OpenAPI
- [x] Notificar todos os admins ao invés de ADMIN_EMAIL fixo
- [ ] Suporte a tablet
- [x] Refresh token rotation
- [x] Limite de tamanho de payload JSON - spring.codec.max-in-memory-size
- [ ] 2FA (TOTP ou e-mail)
- [ ] Reset de senha (precisa de 2FA)
- [ ] Signed URL não revogada imediatamente - limitação conhecida
- [x] UptimeRobot configurado pra monitoramento + alerta de downtime por e-mail
- [ ] GitHub Actions pra build e deploy automático no push pra main
- [ ] Mahoraga Design - em estudo
- [x] Qualidade do pdf no leitor celular está bem inferior do que no pc (só em celular real, navegador no pc em modo celular fica com qualidade boa)
- [x] Botão de feedback na tela principal que abre um formulário de sugestões/feedback que envia no meu email
- [x] Navbar do modo leitura não desaparece no desktop mas fica uma lacuna vazia na parte superior da tela
- [x] Ao terminar de ler um volume, marcar como concluido e resetar página pra 1 + adicionar forma de navegar entre páginas mais rápido
- [ ] Trocar volumes por capitulo (vou analisar se criamos uma tabela de capitulo vinculada ao volume, ou se usamos a tabela de volumes como se fosse capitulo). Finalmente entendi porque sites de mangá usam capítulos ao invés de volumes.
- [ ] Adicionar git/linkedin na tela de login
- [ ] Exibir de alguma forma uma lista com as últimas atualizações na tela principal
- [x] HealthController criado pro UptimeRobot conseguir monitorar a aplicação em produção