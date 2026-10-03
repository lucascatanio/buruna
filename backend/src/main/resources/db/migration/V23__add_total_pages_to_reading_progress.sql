-- Total de páginas do volume, informado pelo leitor ao salvar o progresso.
-- Nulo para progressos antigos, até o volume ser aberto de novo.
ALTER TABLE reading_progress
    ADD COLUMN total_pages INTEGER,
    ADD CONSTRAINT chk_reading_progress_total_pages
        CHECK (total_pages IS NULL OR (total_pages >= 1 AND current_page <= total_pages));
