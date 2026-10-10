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

**Atualização (2026-10-09):** o leitor passou a abrir o PDF com `disableAutoFetch` e
`disableStream`: o pdf.js baixa só os trechos das páginas renderizadas, em vez do arquivo
inteiro em segundo plano. Com isso a URL pode vencer no meio da leitura, quando o range
request da próxima página volta 400/403. O front pede uma URL nova e reabre o documento na
página atual (uma vez por minuto no máximo, para não entrar em laço se a falha não for
expiração). A expiração de 30 min continua como está. Cada renovação passa por
`GET /reader/{volumeId}/url`, então conta mais uma visualização e grava mais uma entrada no
histórico, no máximo uma vez a cada ~30 min de leitura contínua.

**Atualização (2026-10-10):** capítulos de imagens são lidos por um manifesto
(`GET /reader/chapters/{id}`) com uma URL assinada por página. A assinatura V4 embute o
instante em que foi feita, então assinar a mesma página de novo gera outra URL e o navegador
baixaria a imagem outra vez. O `WindowedSignedUrls` (`shared/storage`) guarda a URL de cada
objeto até o fim de uma janela de 1 h; toda URL emitida na janela vale até o fim da janela
seguinte (entre 1 h e 2 h depois de entregue). Quem reabre um capítulo na mesma janela recebe
as mesmas URLs, e o cache do navegador funciona. O cache é em memória, por instância do
Cloud Run; com mais de uma instância, cada uma tem as suas URLs, o que só reduz o
aproveitamento do cache. O manifesto informa `urlsExpireAt` para o leitor saber quando pedir
de novo.
