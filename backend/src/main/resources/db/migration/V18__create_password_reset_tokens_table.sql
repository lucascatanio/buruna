CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    user_id    UUID                     NOT NULL REFERENCES users(id),
    token      VARCHAR(64)              NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
