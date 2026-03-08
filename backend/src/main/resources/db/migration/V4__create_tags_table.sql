CREATE TABLE tags
(
    id          UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    name        VARCHAR(100)             NOT NULL,
    slug        VARCHAR(100)             NOT NULL UNIQUE,
    category_id UUID                     NOT NULL REFERENCES tag_categories (id) ON DELETE RESTRICT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP WITH TIME ZONE
);
