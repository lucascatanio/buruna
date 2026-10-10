# ADR-21 — Progresso de leitura por volume, não por mangá

**Contexto:** O endpoint de progresso poderia retornar o progresso de um volume específico (`GET /reader/{volumeId}/progress`) ou o progresso mais recente do mangá inteiro (`GET /reader/progress/{mangaId}`).

**Decisão:** Progresso por volume. Existe também `GET /reader/progress/{mangaId}` como atalho pra retomar leitura, mas a fonte de verdade é o volume.

**Por quê:** Progresso é inerentemente por volume, cada um tem número de páginas diferente. Armazenar por mangá exigiria lógica extra pra descobrir "qual volume estava sendo lido" e "qual página daquele volume". Por volume é direto: `user_id + volume_id → currentPage`. O endpoint por mangá só busca o `ReadingProgress` mais recente do usuário pra aquele mangá e redireciona.

**Tradeoff:** Pra retomar leitura a partir da tela de detalhes do mangá, precisa de uma query extra pra achar o volume mais recente. Resolvido com o endpoint de atalho.

**Atualização (2026-10-10):** com o capítulo como unidade de leitura
([ADR-44](ADR-44-capitulo-como-unidade-de-leitura.md)), o progresso passa a ser **por
capítulo** (`reading_progress.chapter_id`, V30). Cada linha aponta para um volume ou para um
capítulo, até a migração do legado. Diferenças em relação ao volume: o total de páginas vem do
capítulo no servidor, não do cliente; e o "continuar lendo" de uma obra é o progresso mais
recente entre os capítulos dela (`GET /reader/works/{workId}/chapter-progress`), não o do
maior número, porque capítulos de idiomas diferentes têm numerações independentes.
