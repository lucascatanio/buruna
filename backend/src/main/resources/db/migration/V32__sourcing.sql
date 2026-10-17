-- Contexto sourcing (ADR-47): obras acompanhadas numa fonte externa (MangaDex, ADR-46), as
-- versões de capítulo que a fonte publicou e o lease que garante um executor por fonte.
-- Sem UNIQUE em (fonte, id externo): como no capítulo, a regra fica na aplicação, sob lock
-- (ADR-53); os índices abaixo são só para as consultas dela.

CREATE TYPE external_source AS ENUM ('MANGADEX');
CREATE TYPE tracked_work_status AS ENUM ('ACTIVE', 'PAUSED');
CREATE TYPE source_chapter_state AS ENUM ('PENDING', 'READY', 'FAILED', 'IGNORED');

CREATE TABLE tracked_works
(
    id                   UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    work_id              UUID                     NOT NULL REFERENCES works (id) ON DELETE CASCADE,
    source               external_source          NOT NULL,
    external_id          VARCHAR(100)             NOT NULL,
    status               tracked_work_status      NOT NULL,
    last_checked_at      TIMESTAMP WITH TIME ZONE,
    last_success_at      TIMESTAMP WITH TIME ZONE,
    consecutive_failures INT                      NOT NULL DEFAULT 0,
    last_error           TEXT,
    created_by           UUID                     NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_tracked_works_source_external ON tracked_works (source, external_id);
CREATE INDEX idx_tracked_works_work ON tracked_works (work_id);

-- idiomas acompanhados e o grupo de tradução preferido de cada um
CREATE TABLE tracked_work_languages
(
    tracked_work_id      UUID         NOT NULL REFERENCES tracked_works (id) ON DELETE CASCADE,
    language             VARCHAR(35)  NOT NULL,
    preferred_group_id   VARCHAR(100),
    preferred_group_name VARCHAR(255),
    PRIMARY KEY (tracked_work_id, language)
);

-- uma linha por versão de capítulo na fonte (o mesmo número pode ter uma por grupo)
CREATE TABLE source_chapters
(
    id                  UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    tracked_work_id     UUID                     NOT NULL REFERENCES tracked_works (id) ON DELETE CASCADE,
    external_id         VARCHAR(100)             NOT NULL,
    language            VARCHAR(35)              NOT NULL,
    number              VARCHAR(20),
    volume              VARCHAR(20),
    title               VARCHAR(255),
    group_ids           VARCHAR(500),
    group_names         VARCHAR(500),
    page_count          INT                      NOT NULL,
    published_at        TIMESTAMP WITH TIME ZONE,
    external_updated_at TIMESTAMP WITH TIME ZONE,
    state               source_chapter_state     NOT NULL,
    chapter_id          UUID                     REFERENCES chapters (id) ON DELETE SET NULL,
    last_error          TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_source_chapters_tracked_external ON source_chapters (tracked_work_id, external_id);
CREATE INDEX idx_source_chapters_state ON source_chapters (state, tracked_work_id);

-- um executor por fonte: quem segura o lease até locked_until baixa dessa fonte
CREATE TABLE source_leases
(
    source       external_source PRIMARY KEY,
    holder       VARCHAR(100),
    locked_until TIMESTAMP WITH TIME ZONE
);

INSERT INTO source_leases (source) VALUES ('MANGADEX');
