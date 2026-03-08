CREATE TYPE reading_list_status AS ENUM ('WANT_TO_READ', 'READING', 'COMPLETED', 'DROPPED');

CREATE TABLE reading_list
(
    id         UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    manga_id   UUID                     NOT NULL REFERENCES mangas (id) ON DELETE CASCADE,
    status     reading_list_status      NOT NULL DEFAULT 'WANT_TO_READ',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (user_id, manga_id)
);
