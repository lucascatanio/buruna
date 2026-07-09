# ADR-26 — GCS bucket em southamerica-east1, Cloud Run em us-east1

**Contexto:** O bucket GCS armazena PDFs acessados diretamente pelo browser. Os serviços Cloud Run (backend/frontend) rodam em us-east1.

**Decisão:** Bucket em `southamerica-east1` (São Paulo). Cloud Run em `us-east1` (Carolina do Sul).

**Por quê:** Os PDFs são acessados direto pelo browser via Signed URL. O Cloud Run não faz proxy do conteúdo. Então a latência que importa é browser → GCS, não Cloud Run → GCS. Com o bucket em São Paulo, usuários brasileiros (público-alvo) têm latência baixa no download de PDFs (~20ms vs ~120ms pra us-east1). O Cloud Run fica em us-east1 porque oferece free tier generoso e os requests de API são leves (JSONs de poucos KB). A única comunicação Cloud Run → GCS é no finalize (leitura de metadados, poucos KB, ~100ms de latência cross-region, uma vez por upload).

**Tradeoff:** Latência cross-region de ~100ms na comunicação backend → GCS (só pra operações administrativas: gerar Signed URL, ler metadados no finalize). Imperceptível pro usuário, que já está esperando o upload terminar.
