# ADR-02 — ~~Upload via backend (multipart)~~ → Substituída por ADR-24

**Status:** Substituída

**Contexto original:** Na primeira versão, o upload de volumes passava inteiro pelo backend (multipart/form-data). O backend recebia o arquivo, processava e mandava pro GCS.

**Por que mudou:** O Cloud Run tem limite de memória e tempo de request. PDFs grandes (300–600 MB) causavam timeouts e comiam memória do container. A ADR-24 substituiu isso por upload direto ao GCS via Signed URL.
