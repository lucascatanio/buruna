-- FIND-baixa (hash de tokens): refresh_tokens.token e password_reset_tokens.token
-- passam a guardar SHA-256 em hex (64 chars) em vez do valor em claro. Um vazamento
-- do banco deixa de permitir sequestro de sessão ou reset de senha.
-- Decisão do usuário: apaga os tokens existentes em vez de tentar migrá-los (não dá
-- para hashear um valor que não temos mais depois do deploy) — todo mundo reloga.
DELETE FROM refresh_tokens;
DELETE FROM password_reset_tokens;

ALTER TABLE refresh_tokens ALTER COLUMN token TYPE VARCHAR(64);
