-- Progresso e histórico passam a valer por capítulo (ADR-44), convivendo com os de volume até
-- a migração do legado: cada linha aponta para um volume OU para um capítulo.

ALTER TABLE reading_progress
    ALTER COLUMN volume_id DROP NOT NULL,
    ADD COLUMN chapter_id UUID REFERENCES chapters (id) ON DELETE CASCADE,
    ADD CONSTRAINT chk_reading_progress_target CHECK (num_nonnulls(volume_id, chapter_id) = 1),
    -- chave do upsert, como o UNIQUE(user_id, volume_id) da V8
    ADD CONSTRAINT uq_reading_progress_user_chapter UNIQUE (user_id, chapter_id);

ALTER TABLE reading_history
    ALTER COLUMN volume_id DROP NOT NULL,
    ADD COLUMN chapter_id UUID REFERENCES chapters (id) ON DELETE CASCADE,
    ADD CONSTRAINT chk_reading_history_target CHECK (num_nonnulls(volume_id, chapter_id) = 1);

CREATE INDEX idx_reading_history_chapter_id ON reading_history (chapter_id);
