ALTER TABLE volumes
    ADD CONSTRAINT uq_volumes_file_hash UNIQUE (file_hash);