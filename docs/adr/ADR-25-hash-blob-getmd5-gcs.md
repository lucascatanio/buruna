# ADR-25 — Hash via blob.getMd5() dos metadados do GCS

**Contexto:** Na detecção de duplicatas, precisa do hash do arquivo uploadado. As opções: (a) baixar o arquivo no backend e calcular SHA-256, ou (b) usar o MD5 que o GCS calcula automaticamente no upload.

**Decisão:** Usar `blob.getMd5()` dos metadados do GCS no endpoint de finalize.

**Por quê:** O GCS calcula o MD5 de todo objeto no momento do upload e armazena nos metadados, disponível via API sem baixar o arquivo. Baixar centenas de MB no backend pra calcular SHA-256 local anularia o benefício da ADR-24 (upload direto). O MD5 não é criptograficamente seguro contra colisões intencionais, mas pra detecção de duplicatas acidentais (mesmo PDF uploadado duas vezes) é mais do que o necessário. A probabilidade de colisão acidental em MD5 é ~10⁻³⁸.

**Tradeoff:** MD5 é vulnerável a colisões intencionais. Se um atacante quisesse fazer upload de dois arquivos diferentes com mesmo MD5, conseguiria. No contexto do Burūna (biblioteca pessoal com cadastro aprovado), esse cenário não é realista.
