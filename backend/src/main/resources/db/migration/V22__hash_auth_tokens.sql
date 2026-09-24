-- refresh_tokens.token e password_reset_tokens.token passam a guardar o SHA-256 em hex
-- (64 chars): um vazamento do banco deixa de permitir sequestro de sessão ou reset de
-- senha. Os tokens existentes são apagados em vez de convertidos (ADR-41) — todos os
-- usuários precisam logar de novo.
DELETE FROM refresh_tokens;
DELETE FROM password_reset_tokens;

ALTER TABLE refresh_tokens ALTER COLUMN token TYPE VARCHAR(64);
