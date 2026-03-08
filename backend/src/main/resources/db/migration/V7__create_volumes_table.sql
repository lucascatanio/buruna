CREATE TABLE volumes
(
    id              UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    manga_id        UUID                     NOT NULL REFERENCES mangas (id) ON DELETE CASCADE,
    volume_number   INT                      NOT NULL,
    file_url        VARCHAR(500)             NOT NULL,
    file_hash       VARCHAR(64)              NOT NULL,
    file_size_bytes BIGINT                   NOT NULL,
    uploaded_by     UUID                     NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (manga_id, volume_number)
);

CREATE INDEX idx_volumes_manga_id ON volumes (manga_id);
CREATE INDEX idx_volumes_file_hash ON volumes (file_hash);