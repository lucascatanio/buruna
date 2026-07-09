# ADR-17 — Remoção do UNIQUE(file_hash) em V15

**Contexto:** A V14 adicionou `UNIQUE(file_hash)` na tabela `volumes` pra detectar uploads duplicados. Só que mangás privados de usuários diferentes podem ter o mesmo PDF (ex: mesmo capítulo baixado da mesma fonte).

**Decisão:** Remover a constraint global `UNIQUE(file_hash)` em V15. A verificação de duplicata agora é feita no código, só no momento do promote (privado → público).

**Por quê:** Coleções privadas são independentes. Se dois usuários fazem upload do mesmo arquivo na coleção pessoal, não tem conflito. Cada um tem seu espaço. A unicidade de hash só importa na biblioteca pública, onde duplicatas desperdiçam espaço e confundem a navegação. Mover a validação pro service do promote permite verificar só contra mangás públicos (ver ADR-18).

**Tradeoff:** Volumes com hash duplicado podem existir no bucket GCS (arquivos diferentes, mesmo conteúdo). O custo de storage é mínimo.
