CREATE TABLE refresh_tokens
(
    id         UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    token      VARCHAR(512)             NOT NULL UNIQUE,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);