CREATE TABLE ratings
(
    id         UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    manga_id   UUID                     NOT NULL REFERENCES mangas (id) ON DELETE CASCADE,
    score      INT                      NOT NULL CHECK (score >= 1 AND score <= 5),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (user_id, manga_id)
);

CREATE INDEX idx_ratings_manga_id ON ratings (manga_id);
