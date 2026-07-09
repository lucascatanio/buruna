# ADR-34 — Agregados, fronteiras e Value Objects seletivos

**Status:** Aceita (Fase 2 da refatoração)
**Contexto:** Catálogo público e coleção privada compartilham a entidade `Manga` (flag `is_public`). `Volume` é hoje manipulado por dois services diferentes, com checagem de "número duplicado" e cota espalhadas. `Rating` atualiza `Manga.avgRating`/`ratingCount` na mesma transação. Precisamos de fronteiras de agregado claras sem cair em over-engineering.

**Decisão:**
1. **`Manga` é raiz de agregado e contém `Volume`** (composição, cascade/orphanRemoval). `Volume` só é criado/removido via `Manga` (`addVolume`, `removeVolume`), movendo a invariante de número único e a checagem de cota para dentro do agregado/uso de caso.
2. **Um único agregado `Manga` para público e privado**, distinguido por `isPublic`, com **casos de uso separados** (catálogo × coleção privada × submissão/promoção). Não duplicar entidade/tabela (decisão travada na Fase 1).
3. **`Tag`/`TagCategory`, `Rating`, `ReadingList`, `ReadingProgress` são agregados próprios**, referenciando `Manga`/`User`/`Volume` por id.
4. **Consistência cross-aggregate (`Rating` → `Manga.avgRating`) permanece síncrona** na mesma transação (recálculo), em vez de eventos/eventual consistency.
5. **Value Objects são seletivos:** apenas `Slug`, `VolumeNumber`, `FileHash`, `Quota`, `Email`, `Username`, `Score`. Não transformar toda string/número em VO.

**Por quê:** A raiz `Manga` protege as invariantes de volume num lugar só e elimina a duplicação entre os dois services atuais. Um agregado para os dois modos evita duplicar a tabela `mangas`. Recálculo síncrono é trivial no volume atual; eventos seriam complexidade (e infra) sem retorno (coerente com ADR-03). VOs seletivos encapsulam regra real (range de score, normalização de slug, aritmética de cota) sem poluir o código com wrappers vazios — o que também mantém a leitura simples para quem chega de fora.

**Tradeoff:** Recálculo síncrono acopla a transação de `Rating` ao `Manga` (duas tabelas numa tx) — aceitável e mais simples que consistência eventual. Carregar o agregado `Manga` inteiro para operar um `Volume` pode trazer mais dados que o necessário; mitigado mantendo `VolumeRepository` para leituras/queries específicas, sem violar a fronteira de escrita.
