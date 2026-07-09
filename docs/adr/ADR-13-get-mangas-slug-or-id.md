# ADR-13 — GET /mangas/{slugOrId} resolve UUID ou slug no mesmo endpoint

**Contexto:** Mangás podem ser acessados por slug (URL amigável) ou por UUID (referência interna). A questão era se seriam endpoints separados ou um único.

**Decisão:** Endpoint único que detecta se o parâmetro é UUID (tenta `UUID.fromString()`) ou slug (qualquer outra string).

**Por quê:** Simplifica o roteamento no frontend, que sempre usa `/mangas/{valor}` sem precisar saber se é ID ou slug. Menos duplicação de código no controller. A detecção é trivial e sem ambiguidade (UUIDs têm formato único com hífens e hex).

**Tradeoff:** Se algum dia um slug acabar sendo um UUID válido, ia dar conflito. Na prática, improvável: slugs são gerados a partir de títulos e nunca têm formato UUID.
