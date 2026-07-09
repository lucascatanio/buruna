# ADR-11 — alternative_titles e content_warnings como TEXT (JSON serializado)

**Contexto:** Mangás podem ter múltiplos títulos alternativos e avisos de conteúdo. Precisa armazenar listas de strings.

**Decisão:** Colunas `TEXT` contendo JSON serializado (ex: `["Attack on Titan", "Shingeki no Kyojin"]`) em vez de `TEXT[]` nativo do PostgreSQL.

**Por quê:** `TEXT[]` do PostgreSQL não tem suporte nativo no Hibernate/JPA sem config extra (converters, tipos customizados, dependência do `hibernate-types`). JSON em TEXT é serializável/desserializável sem dor de cabeça com Jackson, funciona com qualquer ORM, e é portável pra outros bancos se necessário. As queries nesses campos são `LIKE` em texto, que funciona igual em JSON serializado e em arrays.

**Tradeoff:** Perde operações nativas de array do PostgreSQL (`ANY`, `@>`, indexação GIN). Pro caso de uso atual (filtro por substring e exibição), não faz diferença.
