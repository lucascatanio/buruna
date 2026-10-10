-- Capítulo como unidade de leitura (ADR-44). Convive com volumes até a migração do legado.
-- Sem UNIQUE em (work_id, language, number): a regra fica na aplicação, sob lock na obra
-- (ADR-53); o índice abaixo é só para a consulta dela ser rápida.

CREATE TYPE chapter_kind AS ENUM ('PAGES', 'FILE');
CREATE TYPE chapter_status AS ENUM ('PROCESSING', 'PUBLISHED', 'FAILED', 'UNPUBLISHED');

CREATE TABLE chapters
(
    id               UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    work_id          UUID                     NOT NULL REFERENCES works (id) ON DELETE CASCADE,
    language         VARCHAR(35)              NOT NULL,
    number           NUMERIC(10, 2),
    label            VARCHAR(100),
    title            VARCHAR(255),
    scanlation_group VARCHAR(255),
    kind             chapter_kind             NOT NULL,
    status           chapter_status           NOT NULL,
    failure_reason   TEXT,
    uploaded_by      UUID                     NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    published_at     TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CHECK (number IS NOT NULL OR label IS NOT NULL)
);

CREATE INDEX idx_chapters_work_language_number ON chapters (work_id, language, number);

CREATE TABLE chapter_pages
(
    chapter_id             UUID         NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    page_index             INT          NOT NULL,
    object_name            VARCHAR(500) NOT NULL,
    data_saver_object_name VARCHAR(500),
    width                  INT          NOT NULL,
    height                 INT          NOT NULL,
    size_bytes             BIGINT       NOT NULL,
    PRIMARY KEY (chapter_id, page_index)
);
