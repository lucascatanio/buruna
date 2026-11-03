-- Fonte manga-downloader (ADR-54): qualquer site que a CLI suporte, identificado pela URL da
-- série. O lease dela vem na V35: o Postgres não deixa usar o valor novo do enum na mesma
-- transação em que ele é criado.

ALTER TYPE external_source ADD VALUE 'MANGA_DOWNLOADER';

-- a URL da série é o id externo dessa fonte, e o id do capítulo no site costuma ser o caminho
-- da URL dele
ALTER TABLE tracked_works ALTER COLUMN external_id TYPE VARCHAR(500);
ALTER TABLE source_chapters ALTER COLUMN external_id TYPE VARCHAR(500);

-- código do idioma como o site o escreve (es-la, Spanish...); nulo no MangaDex, que já usa a
-- tag BCP 47, e vazio no site que não informa idioma
ALTER TABLE tracked_work_languages ADD COLUMN source_language VARCHAR(35);
