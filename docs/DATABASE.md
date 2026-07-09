# Banco de dados

> PostgreSQL, migrations via Flyway. Para infraestrutura de onde o banco roda, veja
> [DEPLOYMENT.md](DEPLOYMENT.md). Vocabulário de domínio (o que cada campo/enum
> significa): [`docs/glossario-dominio.md`](glossario-dominio.md).

## 1. Diagrama de entidades

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
User ──< PasswordResetToken
```

## 2. Constraints e índices relevantes

| Tabela                | Constraint / Índice                          | Observação                          |
|-----------------------|------------------------------------------------|--------------------------------------|
| users                 | UNIQUE(email), UNIQUE(username)                |                                       |
| mangas                | UNIQUE(slug)                                   | Slug gerado com sufixo em conflito   |
| volumes               | UNIQUE(manga_id, volume_number)                | Por mangá, não global                |
| volumes               | INDEX(file_hash)                               | Busca por duplicata no promote       |
| manga_tags            | PK(manga_id, tag_id)                           | Composite PK                         |
| reading_progress      | UNIQUE(user_id, volume_id)                     | Upsert de progresso                  |
| reading_list          | UNIQUE(user_id, manga_id)                      |                                       |
| ratings               | UNIQUE(user_id, manga_id)                      | Uma avaliação por usuário por mangá  |
| refresh_tokens        | INDEX(user_id)                                 | Lookup de tokens por usuário         |
| reading_history       | INDEX(user_id), INDEX(volume_id)               | V16 adicionou index em volume_id     |
| users                 | totp_secret, totp_enabled                      | V17 — colunas para 2FA TOTP          |
| password_reset_tokens | UNIQUE(token), INDEX(user_id), INDEX(token)    | V18 — tokens de reset de senha       |
| mangas                | submission_status, rejection_reason, submitted_at, reviewed_by, reviewed_at | V20 — fluxo de submissão/revisão |

Por que só 7 índices manuais em vez de indexar toda FK: [ADR-09](adr/ADR-09-indices-seletivos-banco.md).
Por que `volumes` não tem mais `UNIQUE(file_hash)` global: [ADR-17](adr/ADR-17-remocao-unique-file-hash-v15.md)
e [ADR-18](adr/ADR-18-promote-valida-unicidade-mangas-publicos.md).

## 3. Migrations Flyway (V1–V20)

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

> `manga_submission_status` (V20) tem só `PENDING`/`REJECTED` — não existe `APPROVED`.
> Assimetria de domínio conhecida, ver [`docs/BACKLOG.md`](BACKLOG.md).

## 4. Convenções de schema

- IDs `UUID`.
- `created_at`/`updated_at` em todas as tabelas de entidade.
- Soft delete via `deleted_at` onde aplicável (ex.: `tags`).
- Enums do domínio armazenados como `ENUM` nativo do Postgres (não string livre).
- Migrations nomeadas `V{n}__{descricao}.sql`, nunca editadas após aplicadas em
  qualquer ambiente — mudança de schema é sempre uma nova migration.
- `alternative_titles` e `content_warnings` em `mangas` são `TEXT` com JSON serializado,
  não `TEXT[]` nativo — ver [ADR-11](adr/ADR-11-alternative-titles-content-warnings-text-json.md).
