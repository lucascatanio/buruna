# ADR-07 — GCS com URLs assinadas (V4)

**Contexto:** PDFs e capas precisam ser acessíveis pelo browser, mas não podem ser públicos pra qualquer um na internet.

**Decisão:** Bucket GCS privado. O backend gera URLs assinadas V4 com expiração por operação (leitura PDF: 30 min, capa privada: 1h, upload PUT: 15 min). O browser acessa o GCS direto com a URL assinada.

**Por quê:** URLs públicas no bucket significaria que qualquer pessoa com o link baixa qualquer PDF, inaceitável pra uma biblioteca que exige cadastro aprovado. URLs assinadas dão acesso temporário e controlado sem que o backend precise servir os bytes (caro em memória e bandwidth no Cloud Run). A expiração curta limita a janela de exposição de cada link.

**Tradeoff:** URLs assinadas não podem ser revogadas antes da expiração. Se um link vazar, ele funciona até expirar. Mitigado pelas expirações curtas (15–30 min).

**Atualização (2026-10-08):** risco aceito, não é mais dívida técnica. O caso que ficava em
aberto é o de revogar o acesso de um usuário (desativação, exclusão de conta, mangá que deixa
de ser visível): as URLs que ele já recebeu continuam valendo até expirar, no máximo 30 min
para o PDF no leitor e 1 h para capas. Encurtar a expiração do leitor não compensa: o pdf.js
baixa o PDF em pedaços (`rangeChunkSize`) ao longo da leitura, e o front guarda a URL por
25 min (`signedUrlCache`) para não pedir outra a cada página. Uma URL mais curta exigiria
renovar no meio da leitura, com risco de travar o leitor, para reduzir uma janela que já é
curta e só expõe o que o usuário já tinha acesso pouco antes.
