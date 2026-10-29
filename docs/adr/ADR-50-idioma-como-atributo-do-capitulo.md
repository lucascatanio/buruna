# ADR-50 — Idioma como atributo do capítulo

**Status:** Aceita

**Contexto:** Com o capítulo como unidade de leitura ([ADR-44](ADR-44-capitulo-como-unidade-de-leitura.md))
e o import do MangaDex ([ADR-46](ADR-46-mangadex-como-fonte-externa.md)), a mesma obra passa a
ter capítulos em vários idiomas, às vezes por grupos diferentes. As opções eram uma obra por
idioma, o idioma na obra, ou o idioma em cada capítulo.

**Decisão:**

- **O idioma é do capítulo** (`chapters.language`), como tag BCP 47 normalizada
  (VO `Language`: `pt-br` vira `pt-BR`). Códigos próprios de uma fonte (`es-la` do MangaDex)
  são traduzidos por quem integra a fonte. A obra é uma só para todos os idiomas, com os mesmos
  metadados, avaliações e lista de leitura.
- **Número único por obra e idioma** (ADR-53): o cap. 12 em pt-BR e o cap. 12 em inglês
  convivem.
- **Na obra**, um seletor de idioma mostra os capítulos de um idioma por vez. O padrão é pt-BR,
  e a escolha fica lembrada no navegador por obra. O progresso é por capítulo, então ler o cap.
  10 em inglês não marca o cap. 10 em pt-BR.
- **No catálogo**, todas as obras aparecem. Cada card mostra os idiomas com capítulo publicado,
  e o filtro "Idioma" mostra só as obras com capítulo publicado nele (`GET /works?language=`;
  a lista do filtro vem de `GET /works/languages`).
- **Volumes legados** não guardam idioma e contam como **pt-BR** no selo e no filtro, o idioma
  de quase todo upload antigo, até a migração do legado (E8) virar capítulo.
- **Livro:** cada edição é um capítulo-arquivo com idioma, e um idioma pode ter várias edições.

**Alternativas descartadas:** uma obra por idioma (duplicaria metadados, avaliações e a lista
de leitura, e o catálogo mostraria a mesma obra várias vezes); idioma na obra (não comporta a
mesma obra traduzida em mais de um idioma, que é o caso normal no MangaDex).

**Tradeoff:** o filtro do catálogo precisa de um `EXISTS` em capítulos (e em volumes, para
pt-BR) por obra. Com o índice `(work_id, language, number)` e algumas centenas de obras, o
custo é pequeno; se o catálogo crescer muito, vale guardar os idiomas na obra como cache.
