-- Capítulo-arquivo (ADR-44): livro em PDF ou EPUB, lido como arquivo inteiro. Cada edição de um
-- livro é um capítulo sem número, identificado pelo rótulo.
CREATE TYPE chapter_file_format AS ENUM ('PDF', 'EPUB');

ALTER TABLE chapters
    ADD COLUMN file_object_name VARCHAR(500),
    ADD COLUMN file_format      chapter_file_format,
    ADD COLUMN file_size_bytes  BIGINT,
    ADD COLUMN file_page_count  INT;

-- EPUB não tem página fixa: a posição é um CFI e o andamento é uma fração de 0 a 1.
ALTER TABLE reading_progress
    ADD COLUMN position TEXT,
    ADD COLUMN percent  NUMERIC(5, 4),
    ADD CONSTRAINT chk_reading_progress_percent CHECK (percent IS NULL OR (percent >= 0 AND percent <= 1));
