# ADR-13 — GET /mangas/{slugOrId} resolve UUID ou slug no mesmo endpoint

**Contexto:** Mangás podem ser acessados por slug (URL amigável) ou por UUID (referência interna). A questão era se seriam endpoints separados ou um único.

**Decisão:** Endpoint único que detecta se o parâmetro é UUID (tenta `UUID.fromString()`) ou slug (qualquer outra string).

**Por quê:** Simplifica o roteamento no frontend, que sempre usa `/mangas/{valor}` sem precisar saber se é ID ou slug. Menos duplicação de código no controller. A detecção é trivial e sem ambiguidade (UUIDs têm formato único com hífens e hex).

**Tradeoff:** Se algum dia um slug acabar sendo um UUID válido, ia dar conflito. Na prática, improvável: slugs são gerados a partir de títulos e nunca têm formato UUID.

**Atualização (2026-10-09):** com o rename para `Work` ([ADR-45](ADR-45-renomear-manga-para-work.md)),
o endpoint passou a ser `GET /works/{slugOrId}`, com a mesma regra de detecção. `/mangas/{slugOrId}`
continua aceito por uma versão, como alias.
