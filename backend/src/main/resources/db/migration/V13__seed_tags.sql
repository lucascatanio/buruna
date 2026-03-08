-- Formato
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Mangá', 'manga', id
FROM tag_categories
WHERE name = 'Formato';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Manhwa', 'manhwa', id
FROM tag_categories
WHERE name = 'Formato';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Manhua', 'manhua', id
FROM tag_categories
WHERE name = 'Formato';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Webtoon', 'webtoon', id
FROM tag_categories
WHERE name = 'Formato';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'One-shot', 'one-shot', id
FROM tag_categories
WHERE name = 'Formato';

-- Demografia
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Shounen', 'shounen', id
FROM tag_categories
WHERE name = 'Demografia';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Shoujo', 'shoujo', id
FROM tag_categories
WHERE name = 'Demografia';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Seinen', 'seinen', id
FROM tag_categories
WHERE name = 'Demografia';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Josei', 'josei', id
FROM tag_categories
WHERE name = 'Demografia';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Kodomo', 'kodomo', id
FROM tag_categories
WHERE name = 'Demografia';

-- Gênero
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Ação', 'acao', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Aventura', 'aventura', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Comédia', 'comedia', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Drama', 'drama', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Fantasia', 'fantasia', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Ficção Científica', 'ficcao-cientifica', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Horror', 'horror', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Mistério', 'misterio', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Romance', 'romance', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Slice of Life', 'slice-of-life', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Sobrenatural', 'sobrenatural', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Suspense', 'suspense', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Esportes', 'esportes', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Psicológico', 'psicologico', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Histórico', 'historico', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Militar', 'militar', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Música', 'musica', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Culinária', 'culinaria', id
FROM tag_categories
WHERE name = 'Gênero';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Artes Marciais', 'artes-marciais', id
FROM tag_categories
WHERE name = 'Gênero';

-- Tema
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Escola', 'escola', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Isekai', 'isekai', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Reencarnação', 'reencarnacao', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Viagem no Tempo', 'viagem-no-tempo', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Poderes / Habilidades', 'poderes', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Apocalipse', 'apocalipse', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Magia', 'magia', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Robôs / Mechas', 'robos-mechas', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Vampiros', 'vampiros', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Zumbis', 'zumbis', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Samurai / Feudal', 'samurai', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Cyberpunk', 'cyberpunk', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Harém', 'harem', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Competição / Torneio', 'competicao', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Vingança', 'vinganca', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Amizade', 'amizade', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Sobrevivência', 'sobrevivencia', id
FROM tag_categories
WHERE name = 'Tema';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Vida Cotidiana', 'vida-cotidiana', id
FROM tag_categories
WHERE name = 'Tema';

-- Aviso de Conteúdo
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'NSFW', 'nsfw', id
FROM tag_categories
WHERE name = 'Aviso de Conteúdo';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Gore', 'gore', id
FROM tag_categories
WHERE name = 'Aviso de Conteúdo';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Gatilho: Suicídio', 'gatilho-suicidio', id
FROM tag_categories
WHERE name = 'Aviso de Conteúdo';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Gatilho: Abuso', 'gatilho-abuso', id
FROM tag_categories
WHERE name = 'Aviso de Conteúdo';
INSERT INTO tags (id, name, slug, category_id)
SELECT gen_random_uuid(), 'Gatilho: Trauma', 'gatilho-trauma', id
FROM tag_categories
WHERE name = 'Aviso de Conteúdo';
