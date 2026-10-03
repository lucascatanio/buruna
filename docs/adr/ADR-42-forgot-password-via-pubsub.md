# ADR-42 — Forgot password via Pub/Sub push (B3): tempo constante contra enumeração de contas

**Status:** Aceita

**Contexto:** `POST /auth/password/forgot` sempre respondia 200, mas o tempo de resposta
denunciava se o e-mail existia: quando existia, a requisição esperava a chamada síncrona
ao Resend (ADR-03, atualização 2026-09-24) — 0,3–2,9 s medidos em produção; quando não
existia, a resposta voltava em milissegundos. Um atacante consegue enumerar contas só
cronometrando o endpoint, sem nenhuma outra informação vazar.

O Cloud Run roda com `cpu-throttling` (a CPU da instância é cortada fora do ciclo de uma
requisição HTTP), o que eliminou algumas soluções óbvias:

- **`@Async`** voltaria a ter o problema já corrigido pelo ADR-03: a thread de fundo perde
  CPU assim que a resposta HTTP é enviada, e a chamada ao Resend trava até dar timeout —
  62 de 84 e-mails falharam em produção com esse padrão.
- **Padding de tempo fixo** (atrasar artificialmente a resposta do caminho "não existe" em
  ~3 s para igualar o pior caso do envio real) resolve o timing mas trava uma requisição
  HTTP inteira só esperando, consumindo cota de concorrência do Cloud Run sem fazer
  trabalho útil — e o padding tem que ser recalibrado toda vez que o tempo do Resend
  mudar.
- **CPU sempre alocada** (desligar `cpu-throttling`) resolveria o `@Async`, mas custa
  ~US$40–45/mês adicionais para o único propósito de manter uma thread de fundo viva — alto
  para o volume do projeto.
- **Outbox + Cloud Scheduler** (gravar o pedido numa tabela e ter um job periódico
  processando) funciona, mas o menor intervalo prático do Scheduler é na casa de minutos:
  o usuário esperaria minutos pelo e-mail de reset, pior experiência que o Pub/Sub push
  (segundos).
- **Token dummy para e-mail inexistente** (gerar e persistir um token que nunca é
  entregável) equaliza o trabalho de banco mas não o de rede — o tempo ainda vaza pela
  chamada ao Resend, que só acontece quando o e-mail existe.

**Decisão:** `POST /auth/password/forgot` sempre publica `{"email": "..."}` num tópico
Pub/Sub (`password-reset-requests`) e responde 200 imediatamente — o tempo de resposta não
depende mais de o e-mail existir, só do round-trip de publicação, que é o mesmo nos dois
casos. Uma push subscription (`password-reset-push`) entrega a mensagem a
`POST /internal/pubsub/password-reset`, endpoint interno que faz o trabalho de fato (buscar
usuário ACTIVE, gerar token, enviar e-mail) dentro de uma requisição HTTP normal — com CPU
alocada, contornando o `cpu-throttling`.

- **Publicação via REST, sem o SDK oficial do Pub/Sub** (`PubSubPublisher`,
  `shared.messaging`): o SDK publica em lote numa thread de fundo, sujeita ao mesmo
  problema de `cpu-throttling` que motivou tirar o `@Async` dos e-mails (ADR-03). A chamada
  REST síncrona (`https://pubsub.googleapis.com/v1/{topic}:publish`) acontece dentro da
  requisição, com token de acesso obtido via Application Default Credentials
  (`PubSubConfig`, `@Profile("!local")`).
- **Autenticação do push é OIDC, verificado no controller** (`PubSubPushAuthenticator`,
  `shared.security`): `/internal/pubsub/**` é `permitAll` no Spring Security (o endpoint
  não tem sessão de usuário para autenticar) e a prova de origem é o token OIDC que o
  Pub/Sub assina em nome da conta de serviço da push subscription
  (`pubsub-push-invoker@buruna.iam.gserviceaccount.com`), verificado com
  `TokenVerifier` (audience + issuer + e-mail da service account + `email_verified`).
- **`PasswordResetRequests`** (`identity.application.account`) é o ponto de entrada único
  do pedido: `PubSubPasswordResetRequests` (`@Profile("!local")`) publica no tópico;
  `InlinePasswordResetRequests` (`@Profile("local")`) processa direto, porque dev/local não
  tem Pub/Sub. O trabalho de fato (busca de usuário, geração/persistência do token, envio
  do e-mail — o mesmo corpo que antes vivia em `AccountService.forgotPassword`) foi extraído
  para `ProcessPasswordResetRequestUseCase`, chamado tanto pelo endpoint de push quanto
  pelo caminho inline.
- **`EmailSender.sendOrFail`** substitui `send` como método abstrato da interface: falha de
  entrega agora propaga `EmailDeliveryException` em vez de só logar WARN e seguir. Isso é o
  que permite o Pub/Sub reentregar a mensagem — o endpoint de push responde 500 (via
  `GlobalExceptionHandler`, sem tratamento especial) quando o e-mail falha, e a
  subscription tenta de novo. Os demais envios (aprovação, rejeição, aviso de inatividade)
  continuam best-effort via `send`, que agora é `default` e engole `EmailDeliveryException`
  — preserva o comportamento anterior onde a falha não bloqueia o fluxo.

**Consequências:**

- O e-mail de reset chega alguns segundos depois da resposta 200, não instantaneamente —
  o usuário não recebe mais confirmação síncrona de que o e-mail foi enviado (nunca recebeu,
  na prática: o endpoint sempre respondeu 200 independente do e-mail existir, mas antes o
  envio acontecia na mesma requisição).
- Falha na entrega aciona o retry padrão do Pub/Sub (backoff exponencial, várias tentativas
  antes de desistir) em vez de simplesmente logar e perder o e-mail — melhora a
  confiabilidade da entrega em relação ao comportamento anterior (best-effort, sem retry).
- A push subscription tem ack deadline de 30 s e retenção de mensagem de 1 h — mensagens
  não confirmadas dentro da retenção são descartadas (consistente com o token de reset
  também expirar em 1 h; não há valor em reentregar depois disso).
- `POST /auth/register` para e-mail já cadastrado continua respondendo 409 imediatamente,
  sem Pub/Sub — esse endpoint tem hCaptcha (ADR-10) e rate limit, que já mitigam
  enumeração por um vetor diferente (tentativas em massa, não timing), então não herdou a
  mesma mudança.
- `/internal/pubsub/**` fica público no Spring Security por necessidade (não há como o
  Pub/Sub apresentar uma sessão de usuário), o que move a responsabilidade de autenticação
  para dentro do controller — desvio documentado e aceito da regra geral de RBAC na borda
  via `@PreAuthorize` (CLAUDE.md), coerente com o padrão já usado por `/admin/jobs/**`
  (segredo compartilhado em vez de OIDC, por não ter push subscription).
