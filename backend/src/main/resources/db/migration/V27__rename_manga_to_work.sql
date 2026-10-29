-- Manga → Work (ADR-45): o catálogo tem livros e quadrinhos, e o nome passa a valer para
-- os dois. Só renomeia; nenhum dado muda. Renomear é operação de metadado no Postgres, sem
-- reescrever as tabelas.

ALTER TABLE mangas RENAME TO works;
ALTER TABLE manga_tags RENAME TO work_tags;

ALTER TABLE work_tags RENAME COLUMN manga_id TO work_id;
ALTER TABLE volumes RENAME COLUMN manga_id TO work_id;
ALTER TABLE ratings RENAME COLUMN manga_id TO work_id;
ALTER TABLE reading_list RENAME COLUMN manga_id TO work_id;

ALTER TYPE manga_format RENAME TO work_format;
ALTER TYPE manga_status_origin RENAME TO work_status_origin;
ALTER TYPE manga_status_site RENAME TO work_status_site;
ALTER TYPE manga_submission_status RENAME TO work_submission_status;

-- Constraints e índices: os nomes gerados pelo Postgres (mangas_pkey, volumes_manga_id_fkey,
-- ratings_user_id_manga_id_key...) não aparecem nas migrations, então são achados no
-- catálogo. Renomear a constraint de PK/UNIQUE renomeia o índice dela junto.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.conname, t.relname
        FROM pg_constraint c
                 JOIN pg_class t ON t.oid = c.conrelid
                 JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = current_schema()
          AND c.conname LIKE '%manga%'
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                       r.relname, r.conname, replace(r.conname, 'manga', 'work'));
    END LOOP;

    FOR r IN
        SELECT indexname
        FROM pg_indexes
        WHERE schemaname = current_schema()
          AND indexname LIKE '%manga%'
    LOOP
        EXECUTE format('ALTER INDEX %I RENAME TO %I',
                       r.indexname, replace(r.indexname, 'manga', 'work'));
    END LOOP;
END
$$;
