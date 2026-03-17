-- índice em reading_history.volume_id (FK sem índice explícito)
-- V9 já criou idx_reading_history_user_id; volume_id não tinha índice
CREATE INDEX IF NOT EXISTS idx_reading_history_volume_id
    ON reading_history(volume_id);

-- índices abaixo NÃO são necessários — já cobertos por constraints existentes:
-- reading_progress(user_id, volume_id): coberto por UNIQUE constraint em V8
-- refresh_tokens(token): coberto por UNIQUE constraint em V2
-- refresh_tokens(user_id): idx_refresh_tokens_user_id já criado em V2
