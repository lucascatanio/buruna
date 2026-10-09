# ADR-44 — Capítulo como unidade de leitura

**Status:** Aceita (substitui a unidade "volume"; complementa ADR-21 e ADR-34)

**Contexto:** A unidade de conteúdo era o volume, um PDF por volume enviado à mão. A
maioria lê no celular, em 4G, e não termina um volume de uma vez. Fontes externas
(MangaDex) publicam por capítulo, e livros em PDF/EPUB são um arquivo só. Um volume de
mangá de 200 páginas é uma unidade grande demais para carregar, acompanhar e sincronizar.

**Decisão:**

1. **`Chapter` é a unidade de leitura** e um agregado próprio do contexto `work`, que
   referencia a obra por `workId`. Gravar um capítulo não carrega a `Work` nem a lista de
   capítulos dela.
2. **Idioma é atributo do capítulo** (VO `Language`, tag BCP 47 normalizada). Capítulos de
   idiomas diferentes são independentes: cada um tem sua numeração e suas páginas.
3. **Número opcional** (VO `ChapterNumber`: decimal, até 2 casas, >= 0). Sem número, o
   capítulo precisa de rótulo ("Extra", "Vol. 3"); o banco garante isso por `CHECK`.
4. **Dois tipos de conteúdo** (`ChapterKind`): `PAGES` (imagens por página, mangá e
   manhwa) e `FILE` (arquivo único, livro em PDF ou EPUB). Páginas ficam em
   `chapter_pages`, com largura e altura para o leitor reservar espaço antes da imagem
   chegar, e uma variante opcional de economia de dados.
5. **Ciclo de vida** (`ChapterStatus`): `PROCESSING` → `PUBLISHED` | `FAILED`;
   `UNPUBLISHED` tira do ar sem apagar. O conteúdo é extraído ou baixado depois do
   registro, então o capítulo nasce em processamento.
6. **Crédito** do grupo de tradução em `scanlation_group`, exigido pela AUP do MangaDex.

**Transição:** `volumes` convive com `chapters` até a migração do legado. Volumes antigos
continuam lidos pelo leitor de PDF até lá.

**Tradeoff:** a regra "número único por obra e idioma" sai do agregado, onde era um método
de `Work` (`addVolume`), e vai para o use case com lock ([ADR-53](ADR-53-regra-de-negocio-na-aplicacao-sem-unique.md)).
Em troca, uma obra com 1.000 capítulos não precisa ser carregada inteira a cada capítulo
novo, e o sync não disputa a linha da obra com a edição de metadados.
