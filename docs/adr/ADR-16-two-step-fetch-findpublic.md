# ADR-16 — Two-step fetch em findPublic (paginar + batch load de tags)

**Contexto:** A listagem de mangás públicos precisa retornar mangás com suas tags. Usar `@EntityGraph` com `Pageable` na mesma query JPA gera o clássico "HHH000104: firstResult/maxResults with collection fetch, applying in memory". O Hibernate carrega tudo e pagina em memória.

**Decisão:** Duas queries: (1) paginação dos mangás sem tags, (2) batch load das tags dos mangás retornados via `WHERE manga_id IN (...)`.

**Por quê:** A alternativa (`@EntityGraph + Pageable`) potencialmente carrega todos os mangás em memória pra depois paginar, anulando o propósito da paginação. O two-step fetch mantém a paginação real no banco e carrega tags só dos mangás que vão ser exibidos. Uma query a mais é bem melhor que risco de OOM com datasets grandes.

**Tradeoff:** Duas queries em vez de uma. Pra N=20 mangás por página, o overhead é irrelevante (~1ms extra).
