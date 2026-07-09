# ADR-24 — Upload direto ao GCS via Signed URL (PUT)

(substitui ADR-02)

**Contexto:** A ADR-02 original fazia upload via backend (multipart). O backend recebia o PDF inteiro, guardava em memória/disco temporário, e reenviava ao GCS. Com PDFs de centenas de MB, isso causava timeouts e uso excessivo de memória no Cloud Run.

**Decisão:** Fluxo de duas fases: (1) backend gera uma Signed URL de PUT com expiração de 15 min, (2) frontend faz PUT direto ao GCS com o arquivo, (3) frontend chama endpoint de finalize no backend pra persistir metadados.

**Por quê:** O backend nunca toca o arquivo, só gera a URL e valida metadados depois do upload. Isso elimina o gargalo de memória e bandwidth no Cloud Run, permite uploads de qualquer tamanho sem timeout, e reduz latência (o browser faz upload direto ao bucket em southamerica-east1, perto do usuário). O finalize consulta só os metadados do blob no GCS (md5, tamanho) sem baixar o arquivo.

**Tradeoff:** Se o upload acontecer mas o finalize não for chamado, o arquivo fica órfão no GCS. Dá pra resolver com lifecycle rule (no backlog) ou job de limpeza periódico.
