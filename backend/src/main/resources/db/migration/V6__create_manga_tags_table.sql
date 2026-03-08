CREATE TABLE manga_tags
(
    manga_id UUID NOT NULL REFERENCES mangas (id) ON DELETE CASCADE,
    tag_id   UUID NOT NULL REFERENCES tags (id) ON DELETE RESTRICT,
    PRIMARY KEY (manga_id, tag_id)
);

CREATE INDEX idx_manga_tags_tag_id ON manga_tags (tag_id);
