-- Arquivo enviado (CBZ) guardado até as páginas serem extraídas. Fica fora de pending/ para
-- a lifecycle rule de 1 dia não apagá-lo antes de uma nova tentativa (ADR-48). Conta na
-- quota enquanto existir.
ALTER TABLE chapters
    ADD COLUMN source_object_name VARCHAR(500),
    ADD COLUMN source_size_bytes  BIGINT;
