# ADR-51 — Leitor de EPUB com foliate-js, com o conteúdo do livro isolado

**Status:** Aceita

**Contexto:** Livro em EPUB é lido como arquivo inteiro ([ADR-44](ADR-44-capitulo-como-unidade-de-leitura.md)).
EPUB é um zip de XHTML, CSS, fontes e imagens enviado por usuário, e XHTML pode trazer
`<script>`, handlers `on*` e links `javascript:`. O leitor roda na origem do app, a mesma do
token de acesso, então script do livro não pode rodar.

**Decisão:**

- **foliate-js 1.0.1** (MIT), sem build próprio: monta o livro no navegador, pagina em colunas
  ou rola, entrega posição em CFI e andamento de 0 a 1, e lê o sumário. Carregado com
  `import()` só quando alguém abre um EPUB, para não pesar no bundle de quem lê quadrinho.
- **Arquivo inteiro, uma vez.** EPUB é zip e não dá para ler por trechos como o PDF. O front
  baixa o arquivo pela URL assinada; com 400 ou 403 pede outra pelo manifesto de prefetch.
- **Isolamento em duas camadas.** O foliate-js abre cada capítulo do livro num iframe
  `blob:` com `sandbox="allow-same-origin allow-scripts"` (o `allow-scripts` contorna um bug
  do WebKit com eventos), ou seja, o sandbox sozinho não protege. Por isso:
  1. **CSP herdado.** Documento `blob:` herda o CSP da página. O `script-src` do nginx não
     tem `'unsafe-inline'` nem `blob:`, então script inline, handler `on*`, `javascript:` e
     script do zip ficam barrados. O CSP ganhou `blob:` só em `frame-src`, `style-src` e
     `font-src`, para o iframe, o CSS e as fontes do livro.
  2. **Limpeza antes da carga.** O `EpubReader` escuta o evento `data` do carregador do
     foliate-js (todo recurso passa por ele antes de virar `blob:`) e tira do XHTML/SVG
     `script`, `iframe`, `object`, `embed`, `form`, `base`, `meta http-equiv`, atributos `on*` e
     URLs `javascript:`. O foliate-js já descarta os arquivos de script do manifesto.
- **Link externo** abre em aba nova com `noopener,noreferrer`, e só `http(s)`.
- **Preferências no navegador:** tema (claro, sépia, escuro), fonte (do livro, serifada, sem
  serifa), tamanho (80% a 160%) e modo (página a página ou rolagem), em `localStorage`.
- O backend só valida a estrutura mínima (`mimetype`, `container.xml`, OPF, com XML sem DTD;
  `BookFileValidator`). O HTML não é inspecionado no servidor.

**Verificado** num Chrome headless com o CSP do `nginx.conf`: um EPUB com `<script>` inline,
`<img onerror>` e link `javascript:` não executou nada, e um iframe `blob:` criado à mão com
script inline foi bloqueado pelo CSP herdado (o `frame-ancestors 'none'` não impede o iframe
`blob:`).

**Alternativas descartadas:** epub.js (sem manutenção ativa, maior); converter o EPUB em HTML
limpo no Job (mais código no backend e perde a paginação do leitor); iframe com `sandbox` sem
`allow-same-origin` (o foliate-js precisa acessar o documento do iframe para paginar).

**Tradeoff:** a segurança depende do CSP continuar sem `'unsafe-inline'` e sem `blob:` em
`script-src`. Quem mexer no CSP precisa ler este ADR. O EPUB inteiro é baixado antes da
primeira página, o que num livro grande custa alguns segundos em 4G.
