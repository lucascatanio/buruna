CREATE TABLE reading_progress
(
    id           UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    user_id      UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    volume_id    UUID                     NOT NULL REFERENCES volumes (id) ON DELETE CASCADE,
    current_page INT                      NOT NULL DEFAULT 1,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (user_id, volume_id)
);
