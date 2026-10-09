# ADR-43 — Segredo compartilhado nginx → backend contra chamada direta ao `run.app`

**Status:** Aceita

**Contexto:** O `buruna-backend` (Cloud Run) tem ingress público e o nginx do frontend faz
`proxy_pass` para a URL pública `run.app` dele (sem VPC). Quem chama o `run.app` direto
controla o `X-Forwarded-For` que o `ClientIpResolver` lê para o rate limit de login,
cadastro e forgot password, e consegue escolher qualquer IP a cada tentativa. O
`APP_TRUSTED_PROXY_HOPS` só protege o caminho que passa pelo nginx.

Alternativas descartadas:

- **Ingress `internal-and-cloud-load-balancing` ou VPC connector + ingress interno:** exige
  VPC, Serverless NAT ou load balancer, com custo fixo mensal e mais infraestrutura que o
  projeto sustenta hoje.
- **IAM (`roles/run.invoker`) com token OIDC do frontend:** o nginx não assina tokens do
  Google; exigiria um sidecar ou trocar o proxy por código.

**Decisão:** segredo compartilhado, no mesmo estilo do `X-Job-Secret`.

- O nginx envia `X-Proxy-Secret: ${BACKEND_PROXY_SECRET}` em todo `/api/` (`envsubst` na
  subida do container).
- `ProxySecretFilter` (`shared/security`) responde 403 (`ErrorResponse`) a toda requisição
  sem o header correto, com comparação em tempo constante (`MessageDigest.isEqual`). Roda
  antes do `RateLimitFilter`, para o atacante nem consumir nem escolher chave de rate limit.
- A propriedade é `app.proxy.secret` (`APP_PROXY_SECRET`). **Vazia, o filtro fica desligado**
  e loga WARN no startup: dev local e testes não passam por nginx. O custo é que produção
  esquecida sem a variável fica aberta; o aviso e o `docs/DEPLOYMENT.md` mitigam, e
  tornar a variável obrigatória quebraria o profile `local`.
- Exceções, por terem autenticação própria ou chamarem o container sem passar pelo nginx:
  `/internal/pubsub/**` (OIDC), `/admin/jobs/**` (`X-Job-Secret`) e `/health` (probe do
  Cloud Run; devolve só `{"status":"UP"}`). Os caminhos são avaliados relativos ao
  context-path `/api`.

**Consequências:**

- O `ClientIpResolver` e o `APP_TRUSTED_PROXY_HOPS` não mudam: o caminho nginx → Cloud Run
  continua o mesmo, e a "limitação conhecida" de chamada direta deixa de existir.
- O frontend e o backend dependem do mesmo segredo no Secret Manager. O rollout tem ordem
  obrigatória (frontend antes do backend) e a rotação tem uma janela curta de 403 — ver
  `docs/DEPLOYMENT.md`.
- Quem tem o segredo (qualquer um com acesso ao Secret Manager ou ao container do
  frontend) volta a poder forjar o `X-Forwarded-For`; é o mesmo nível de confiança dos
  demais segredos do projeto.
