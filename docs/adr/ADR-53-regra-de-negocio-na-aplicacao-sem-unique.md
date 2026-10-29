# ADR-53 — Regra de unicidade de código novo na aplicação, sem UNIQUE no banco

**Status:** Aceita

**Contexto:** O capítulo saiu do agregado `Work` ([ADR-44](ADR-44-capitulo-como-unidade-de-leitura.md)),
então a regra "número único por obra e idioma" deixou de ser um método do agregado. O caminho
usual seria um `UNIQUE (work_id, language, number)`. A decisão foi não deixar regra de negócio
no banco: o banco não sabe, por exemplo, que um capítulo `FAILED` não ocupa o número, e a
regra fica num lugar só, legível para quem chega pelo código.

Sem constraint, a checagem por consulta sozinha não basta: duas gravações simultâneas (sync e
upload do ADM, ou uma reentrega do Job) passariam as duas pela checagem antes de qualquer
insert.

**Decisão:**

- A regra fica no use case (`RegisterChapterUseCase`), checada por consulta
  (`existsBy...`) com um **índice comum** em `(work_id, language, number)`, que é só
  desempenho.
- Um **lock pessimista na linha da obra** (`WorkRepository.lockById`, `@Lock(PESSIMISTIC_WRITE)`
  em JPQL, aceito pelo ArchUnit) serializa os registros na mesma obra. O segundo espera o
  primeiro e enxerga o capítulo já gravado. Obras diferentes não esperam umas pelas outras.
- O lock cobre só o passo final (conferir e gravar), nunca download nem conversão de páginas,
  para não segurar a linha nem uma conexão do pool por minutos.
- Vale para **código novo** (capítulo e o contexto de importação). Os UNIQUEs que já existem
  (slug, e-mail, username, progresso por usuário) ficam como estão.

**Alternativas descartadas:** lock otimista com `@Version` na obra (exigiria retry e faria
toda edição da obra poder falhar com conflito); escritor único sem trava (Pub/Sub e Cloud
Run Jobs entregam pelo menos uma vez, então duplicata não seria rara); id determinístico
derivado de (obra, idioma, número) (renumerar um capítulo mudaria o id e quebraria progresso
e histórico).

**Tradeoff:** a regra só vale se **todo** caminho que grava capítulo passar pelo use case.
Nenhum guard automatizado detecta um use case novo que salve direto pelo repositório; isso
fica para a revisão de código. O teste `RegisterChapterIntegrationTest` corre duas
transações reais em paralelo e falha se o lock sair.
