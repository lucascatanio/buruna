# Banco de dados

> PostgreSQL, migrations via Flyway. Para infraestrutura de onde o banco roda, veja
> [DEPLOYMENT.md](DEPLOYMENT.md). Vocabulário de domínio (o que cada campo/enum
> significa): [`docs/glossario-dominio.md`](glossario-dominio.md).

## 1. Diagrama de entidades

```
User ──────────────────< Work (owner_id)
                         │
                         ├──< Volume (legado, até a migração para capítulos)
                         │     └── file_url (objectName no GCS)
                         │         file_hash (MD5 via metadados GCS)
                         │
                         ├──< Chapter (work_id, sem FK de agregado: ADR-44)
                         │     ├── language, number?, label?, kind, status
                         │     └──< ChapterPage (chapter_pages: posição, objectName, largura, altura)
                         │
                         └──>──< Tag (via WorkTag)
                                  └──> TagCategory

Tag >──────────────────── TagCategory

User ──< ReadingProgress >────── Volume | Chapter
User ──< ReadingHistory  >────── Volume | Chapter
User ──< ReadingList     >────── Work
User ──< Rating          >────── Work
User ──< RefreshToken
User ──< PasswordResetToken
```

## 2. Constraints e índices relevantes

| Tabela                | Constraint / Índice                          | Observação                          |
|-----------------------|------------------------------------------------|--------------------------------------|
| users                 | UNIQUE(email), UNIQUE(username)                |                                       |
| works                 | UNIQUE(slug)                                   | Slug gerado com sufixo em conflito   |
| volumes               | UNIQUE(work_id, volume_number)                 | Por obra, não global                 |
| volumes               | INDEX(file_hash)                               | Busca por duplicata no promote       |
| work_tags             | PK(work_id, tag_id)                            | Composite PK                         |
| reading_progress      | UNIQUE(user_id, volume_id)                     | Upsert de progresso                  |
| reading_list          | UNIQUE(user_id, work_id)                       |                                       |
| ratings               | UNIQUE(user_id, work_id)                       | Uma avaliação por usuário por obra   |
| refresh_tokens        | INDEX(user_id)                                 | Lookup de tokens por usuário         |
| reading_history       | INDEX(user_id), INDEX(volume_id)               | V16 adicionou index em volume_id     |
| users                 | totp_secret, totp_enabled                      | V17 — colunas para 2FA TOTP          |
| password_reset_tokens | UNIQUE(token), INDEX(user_id), INDEX(token)    | V18 — tokens de reset de senha       |
| works                 | submission_status, rejection_reason, submitted_at, reviewed_by, reviewed_at | V20 — fluxo de submissão/revisão |
| users                 | totp_last_used_step, totp_failed_attempts, totp_locked_until | V21 — força bruta e replay de TOTP |
| refresh_tokens        | token VARCHAR(64)                              | V22 — SHA-256 hex do token, não mais o valor em claro |
| reading_progress      | total_pages, CHECK(current_page <= total_pages) | V23 — total de páginas do volume (nulo até o leitor informar) |
| chapters              | INDEX(work_id, language, number), CHECK(number OR label) | V28 — sem UNIQUE: número único por obra e idioma é regra da aplicação, sob lock na obra ([ADR-53](adr/ADR-53-regra-de-negocio-na-aplicacao-sem-unique.md)) |
| chapter_pages         | PK(chapter_id, page_index)                     | V28 — páginas de um capítulo de imagens |
| reading_progress      | UNIQUE(user_id, chapter_id), CHECK(volume_id XOR chapter_id) | V30 — progresso por capítulo, convivendo com o de volume |
| reading_history       | INDEX(chapter_id), CHECK(volume_id XOR chapter_id) | V30 |

Por que só 7 índices manuais em vez de indexar toda FK: [ADR-09](adr/ADR-09-indices-seletivos-banco.md).
Por que `volumes` não tem mais `UNIQUE(file_hash)` global: [ADR-17](adr/ADR-17-remocao-unique-file-hash-v15.md)
e [ADR-18](adr/ADR-18-promote-valida-unicidade-mangas-publicos.md).

## 3. Migrations Flyway (V1–V31)

> Verificado em `backend/src/main/resources/db/migration/` — atualize esta tabela ao
> adicionar uma migration nova.

| Versão | Descrição                                                        |
|--------|-------------------------------------------------------------------|
| V1     | Tabela users (enums role, status)                                  |
| V2     | Tabela refresh_tokens                                              |
| V3     | Tabela tag_categories                                              |
| V4     | Tabela tags (soft delete com deleted_at)                           |
| V5     | Tabela mangas (enums format, status_origin, status_site)           |
| V6     | Tabela manga_tags (junction)                                       |
| V7     | Tabela volumes                                                     |
| V8     | Tabela reading_progress                                            |
| V9     | Tabela reading_history                                             |
| V10    | Tabela reading_list (enum status)                                  |
| V11    | Tabela ratings (check score 1–5)                                   |
| V12    | Seed: categorias de tags                                           |
| V13    | Seed: 54+ tags iniciais                                            |
| V14    | Adicionou UNIQUE(file_hash) em volumes                             |
| V15    | Removeu UNIQUE(file_hash) — mangás privados podem ter mesmo hash   |
| V16    | Adicionou INDEX(volume_id) em reading_history                      |
| V17    | Adicionou colunas totp_secret e totp_enabled em users              |
| V18    | Tabela password_reset_tokens (reset de senha)                      |
| V19    | Adicionou valor `LIVRO` ao enum manga_format                       |
| V20    | Colunas de submissão/revisão em mangas (submission_status, rejection_reason, submitted_at, reviewed_by, reviewed_at) |
| V21    | Colunas totp_last_used_step, totp_failed_attempts, totp_locked_until em users (força bruta e replay de TOTP) |
| V22    | Apaga refresh_tokens e password_reset_tokens (valores em claro descartados) e encolhe refresh_tokens.token para VARCHAR(64) — os dois passam a guardar SHA-256 hex (ADR-41) |
| V23    | Coluna total_pages em reading_progress (nula para progressos antigos) e CHECK de página dentro do total |
| V24    | Adicionou valor `APPROVED` ao enum manga_submission_status                         |
| V25    | Backfill: aprovados antigos (público + reviewed_at, status nulo) → `APPROVED`; público com `PENDING`/`REJECTED` (promovido com submissão aberta) → status e motivo nulos |
| V26    | Adicionou valor `DELETED` ao enum user_status (conta anonimizada)                  |
| V27    | Renomeia mangas → works, manga_tags → work_tags, colunas manga_id → work_id, tipos enum manga_* → work_* e constraints/índices ([ADR-45](adr/ADR-45-renomear-manga-para-work.md)) |
| V28    | Tabelas chapters e chapter_pages, enums chapter_kind e chapter_status ([ADR-44](adr/ADR-44-capitulo-como-unidade-de-leitura.md)) |
| V29    | Colunas source_object_name e source_size_bytes em chapters: arquivo enviado guardado até a extração das páginas ([ADR-48](adr/ADR-48-ingest-em-cloud-run-job.md)) |
| V30    | reading_progress e reading_history ganham chapter_id (volume_id passa a ser opcional; CHECK de exatamente um dos dois) e UNIQUE(user_id, chapter_id) no progresso ([ADR-21](adr/ADR-21-progresso-leitura-por-volume.md), atualização) |
| V31    | Capítulo-arquivo (livro): chapters.file_object_name, file_format (enum chapter_file_format: PDF, EPUB), file_size_bytes, file_page_count; reading_progress.position (CFI) e percent (0 a 1) para EPUB ([ADR-44](adr/ADR-44-capitulo-como-unidade-de-leitura.md), [ADR-21](adr/ADR-21-progresso-leitura-por-volume.md)) |

> Valores de enum novos (`ALTER TYPE ... ADD VALUE`) não podem ser usados na mesma
> transação em que foram criados, e o Flyway roda cada migration numa transação: um backfill
> que use o valor novo vai numa migration separada (ex.: V24 + V25).

## 4. Convenções de schema

- IDs `UUID`.
- `created_at`/`updated_at` em todas as tabelas de entidade.
- Soft delete via `deleted_at` onde aplicável (ex.: `tags`).
- Enums do domínio armazenados como `ENUM` nativo do Postgres (não string livre).
- Migrations nomeadas `V{n}__{descricao}.sql`, nunca editadas após aplicadas em
  qualquer ambiente — mudança de schema é sempre uma nova migration.
- `alternative_titles` e `content_warnings` em `works` são `TEXT` com JSON serializado,
  não `TEXT[]` nativo — ver [ADR-11](adr/ADR-11-alternative-titles-content-warnings-text-json.md).
