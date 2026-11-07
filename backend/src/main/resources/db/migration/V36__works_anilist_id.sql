-- Obra no AniList (ADR-54): o import de site acha por ele a obra que já existe, em vez de criar
-- outra. Sem UNIQUE (ADR-53): a checagem fica no import, sob lock no lease da fonte.
ALTER TABLE works ADD COLUMN anilist_id INTEGER;
CREATE INDEX idx_works_anilist_id ON works (anilist_id);
