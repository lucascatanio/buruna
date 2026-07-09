# ADR-04 — nginx como reverse proxy no frontend

**Contexto:** O frontend é uma SPA React que precisa de: servir arquivos estáticos, fazer proxy de `/api/*` para o backend, e suportar client-side routing (fallback `try_files`).

**Decisão:** Container Docker com nginx servindo o build estático e fazendo proxy pass para o backend via VPC connector.

**Por quê:** O nginx é leve (~5 MB de imagem), sólido pra servir estáticos e fazer proxy, e não precisa de Node.js em produção. A alternativa seria um API Gateway externo (tipo o GCP API Gateway), que ia adicionar custo, latência e configuração para um ganho mínimo quando o projeto tem um backend só.

**Tradeoff:** Sem features de API Gateway (rate limiting centralizado, API keys, transformações de request). O que é necessário disso está implementado no próprio backend.
