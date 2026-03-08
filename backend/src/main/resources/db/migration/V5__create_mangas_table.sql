CREATE TYPE manga_format AS ENUM ('MANGA', 'MANHWA', 'MANHUA', 'WEBTOON', 'ONESHOT');
CREATE TYPE manga_status_origin AS ENUM ('ONGOING', 'COMPLETED', 'HIATUS', 'CANCELLED');
CREATE TYPE manga_status_site AS ENUM ('COMPLETE', 'INCOMPLETE');

CREATE TABLE mangas
(
    id                 UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    slug               VARCHAR(255)             NOT NULL UNIQUE,
    title              VARCHAR(255)             NOT NULL,
    alternative_titles TEXT,
    synopsis           TEXT,
    cover_url          VARCHAR(500),
    format             manga_format             NOT NULL,
    origin_country     VARCHAR(100),
    status_origin      manga_status_origin      NOT NULL,
    status_site        manga_status_site        NOT NULL,
    year               INT,
    content_warnings   TEXT,
    avg_rating         DECIMAL(3, 2)            NOT NULL DEFAULT 0.00,
    rating_count       INT                      NOT NULL DEFAULT 0,
    view_count         INT                      NOT NULL DEFAULT 0,
    is_public          BOOLEAN                  NOT NULL DEFAULT false,
    owner_id           UUID                     NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_mangas_owner_id ON mangas (owner_id);