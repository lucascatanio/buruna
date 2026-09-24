ALTER TABLE users ADD COLUMN totp_last_used_step BIGINT NULL;
ALTER TABLE users ADD COLUMN totp_failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN totp_locked_until TIMESTAMPTZ NULL;
