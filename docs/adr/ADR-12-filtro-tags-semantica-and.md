# ADR-12 — Filtro de tags com semântica AND

**Contexto:** O usuário filtra mangás por tags (ex: "Ação" + "Seinen"). A query precisa decidir se retorna mangás que têm *todas* as tags selecionadas (AND) ou *qualquer uma* delas (OR).

**Decisão:** Semântica AND. O mangá precisa ter todas as tags selecionadas pra aparecer no resultado.

**Por quê:** O caso de uso é refinar busca. Se o usuário seleciona "Ação" + "Seinen", quer mangás que são ambos, não qualquer mangá de ação ou qualquer seinen. OR amplia demais os resultados e torna os filtros quase inúteis quando o usuário marca mais de uma tag. MangaDex e Anilist usam AND como padrão.

**Tradeoff:** Combinações muito específicas de tags podem retornar zero resultados. O frontend pode mitigar mostrando a contagem de resultados em tempo real enquanto o usuário adiciona tags.
