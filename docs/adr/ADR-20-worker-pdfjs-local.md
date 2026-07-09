# ADR-20 — Worker pdfjs servido localmente via public/

**Contexto:** O pdfjs-dist precisa de um Web Worker pra renderizar PDFs. Esse worker pode vir de um CDN público ou ser servido localmente.

**Decisão:** Worker copiado pra `public/` e servido pelo próprio nginx do frontend.

**Por quê:** CDNs (unpkg, jsdelivr) são uma dependência externa em runtime. Se o CDN cair ou mudar a URL, o leitor para de funcionar. Servir localmente garante que o worker está disponível enquanto o frontend estiver no ar. O custo de bandwidth é irrelevante (o worker tem ~500 KB e o browser cacheia).

**Tradeoff:** Atualizar a versão do worker exige rebuild do frontend. Aceitável, já que atualizar pdfjs já exige rebuild de qualquer forma.
