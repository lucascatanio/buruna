# ADR-19 — pdfjs-dist v4 (não v5)

**Contexto:** O leitor de mangá usa pdfjs-dist pra renderizar PDFs no browser.

**Decisão:** Manter pdfjs-dist v4 em vez de migrar pra v5.

**Por quê:** A v5 introduziu breaking changes na API (Worker API reformulada, importação de módulos, remoção de compatibilidade com builds legacy) que exigiriam refatoração pesada do componente de leitura. A v4 é estável, atende tudo que o leitor precisa, e recebe patches de segurança. Migrar pra v5 pode fazer sentido no futuro se alguma feature nova exclusiva da v5 justificar o esforço.

**Tradeoff:** A v4 vai entrar em end-of-life eventualmente. Monitorar e planejar migração quando patches de segurança pararem.
