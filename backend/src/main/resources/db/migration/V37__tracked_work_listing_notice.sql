-- Aviso da última listagem (#142): idioma acompanhado que voltou sem capítulos enquanto o site
-- listou outros (código do idioma no site errado ou vazio). Nulo quando todo idioma veio com algo.
ALTER TABLE tracked_works ADD COLUMN listing_notice TEXT;
