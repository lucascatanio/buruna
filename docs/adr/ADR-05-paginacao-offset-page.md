# ADR-05 — Paginação offset/page

**Contexto:** Listagens de mangás, usuários, histórico de leitura, tudo precisa de paginação.

**Decisão:** Paginação offset-based com `Pageable` do Spring Data (`?page=0&size=20`).

**Por quê:** Implementação trivial com Spring Data JPA. O dataset é pequeno (centenas de mangás, dezenas de usuários) e as listagens são ordenadas por campos indexados. Cursor-based pagination seria necessária com milhões de registros ou feeds infinitos com inserções frequentes. Nenhum desses casos existe no Burūna.

**Tradeoff:** Em datasets grandes, `OFFSET` tem performance O(n), o banco percorre N registros antes de retornar. Irrelevante no tamanho atual.
