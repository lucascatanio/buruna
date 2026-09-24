# Segurança

## Reportando uma vulnerabilidade

Encontrou um problema de segurança? Não abra uma issue pública. Envie um e-mail para
o mantenedor (ver `ADMIN_EMAIL` configurado no projeto, ou contato no perfil do
GitHub) descrevendo o problema e, se possível, um passo a passo para reproduzir.
Responderemos e coordenaremos a correção antes de qualquer divulgação pública.

## Ciclo de vida do JWT + Refresh Token

```
Login (sem 2FA)
  ├── accessToken  (JWT, assinado com JWT_SECRET, claim typ=access)
  │   expira em:  JWT_EXPIRATION segundos (padrão: 3600 = 1h)
  │   contém:     userId, role
  │   usado em:   Authorization: Bearer <token> (só em memória no frontend)
  │
  └── refreshToken (aleatório, 256 bits)
      expira em:  REFRESH_TOKEN_EXPIRATION segundos (padrão: 604800 = 7 dias)
      viaja em:   cookie httpOnly buruna_refresh (HttpOnly; Secure; SameSite=Strict;
                  Path=/api/auth) — nunca no corpo JSON, nunca visível ao JavaScript
      armazenado: tabela refresh_tokens (SHA-256 hex do token + user_id + expires_at —
                  o banco nunca vê o valor em claro)
      usado em:   POST /auth/refresh (lido via cookie) para obter novo accessToken
      invalidado: no logout OU ao ser usado (rotação de token)

Login (com 2FA)
  POST /auth/login { email, password }
  → se totpEnabled == true: retorna { requires2FA: true, tempToken }
    tempToken: JWT com claim "purpose":"2fa" (sem "typ":"access" — não serve como
    Bearer de nenhum outro endpoint), expira em 5 min
  → POST /auth/2fa/authenticate { tempToken, totpCode }
  → valida tempToken + código TOTP (força bruta e replay bloqueados no agregado User)
  → retorna accessToken no corpo e grava o cookie buruna_refresh, como no login sem 2FA

Refresh (com rotação)
  POST /auth/refresh (sem corpo; refreshToken vem do cookie buruna_refresh)
  → backend hasheia o valor do cookie e busca no banco, valida expires_at
  → deleta token antigo, gera e persiste um novo (hash)
  → retorna { accessToken, expiresIn } no corpo e regrava o cookie com o novo valor
  → sem cookie: 401

Logout
  POST /auth/logout (sem corpo; refreshToken vem do cookie, se presente)
  → backend deleta o token do banco (se o cookie existir)
  → resposta zera o cookie (Max-Age=0) — idempotente mesmo sem cookie
```

Cada chamada a `/auth/refresh` invalida o token usado e emite um novo — se um refresh
token vazar, a janela de exploração cai de 7 dias para um único ciclo. Detalhe e
tradeoff: [ADR-28](docs/adr/ADR-28-refresh-token-rotation.md). O cookie httpOnly (em vez
de `localStorage`) e o hash do token no banco (em vez do valor em claro) são
[ADR-41](docs/adr/ADR-41-refresh-token-cookie-httponly-e-hash.md) — inclusive por que
`SameSite=Strict` dispensa um token CSRF dedicado nesse fluxo.

> ⚠️ Não há UI para `DELETE /auth/account`. Ver [`docs/BACKLOG.md`](docs/BACKLOG.md).

## Senhas

BCrypt via `PasswordEncoder` do Spring Security — hashing adaptativo com salt
automático, deliberadamente mais lento que SHA-256 para dificultar brute force.
Decisão: [ADR-06](docs/adr/ADR-06-bcrypt-hashing-senhas.md).

## 2FA (TOTP)

Compatível com Google Authenticator, Authy e qualquer app RFC 6238. Quando habilitado,
é exigido tanto no login quanto no reset de senha. Sem recovery code — perda do
dispositivo exige intervenção manual de um admin. Decisão: [ADR-30](docs/adr/ADR-30-2fa-totp-google-authenticator.md).

## RBAC (controle de acesso por role)

| Ação                                     | Visitante | READER | COLLABORATOR | ADMIN |
|------------------------------------------|-----------|--------|--------------|-------|
| Registro e login                         | ✅        | —      | —            | —     |
| Biblioteca (listagem, detalhes)          | ❌        | ✅     | ✅           | ✅    |
| Leitor de PDF (público)                  | ❌        | ✅     | ✅           | ✅    |
| Lista de leitura e avaliações            | ❌        | ✅     | ✅           | ✅    |
| Coleção privada (criar, editar, deletar) | ❌        | ✅     | ✅           | ✅    |
| Criar mangá público                      | ❌        | ❌     | ✅           | ✅    |
| Upload de volume público                 | ❌        | ❌     | ✅           | ✅    |
| Promover privado → público               | ❌        | ❌     | ✅           | ✅    |
| Painel admin (usuários, dashboard)       | ❌        | ❌     | ❌           | ✅    |
| Gerenciar tags e categorias              | ❌        | ❌     | ❌           | ✅    |
| Alterar role/status/cota de usuários     | ❌        | ❌     | ❌           | ✅    |
| Enviar feedback (`POST /feedback`)       | ❌        | ✅     | ✅           | ✅    |

Usuários com status `PENDING` ou `INACTIVE` são bloqueados no login. RBAC é aplicado
na borda (`@PreAuthorize` no controller); ownership (posse de um recurso) é uma regra
de `application`, verificada por `actorId` — nunca no domínio de outro contexto. Ver
[ADR-35](docs/adr/ADR-35-autorizacao-unificada.md).

## Rate limiting

`RateLimitFilter` (in-memory `ConcurrentHashMap`), IP detectado via
`X-Forwarded-For` (compatível com Cloud Run):

| Endpoint                    | Limite padrão | Variável de env                      |
|------------------------------|----------------|----------------------------------------|
| POST /auth/register          | 5 req/hora     | RATE_LIMIT_REGISTER_PER_HOUR          |
| POST /auth/login             | 10 req/hora    | RATE_LIMIT_LOGIN_PER_HOUR             |
| POST /auth/password/forgot   | 3 req/hora     | RATE_LIMIT_FORGOT_PASSWORD_PER_HOUR   |

Retorna `429 Too Many Requests` quando o limite é excedido. Entradas expiradas são
limpas via `@Scheduled` a cada 1 hora.

## hCaptcha

`POST /auth/register` exige um token `captchaToken` validado contra
`https://api.hcaptcha.com/siteverify`. Em desenvolvimento, sem `HCAPTCHA_SECRET`
configurado, a validação é pulada automaticamente (`CaptchaService`). Rate limit +
hCaptcha combinados: [ADR-10](docs/adr/ADR-10-rate-limit-hcaptcha-registro.md).

## Arquivos (GCS)

Bucket privado; acesso só via URLs assinadas V4 de expiração curta (leitura de PDF:
30 min; capa privada: 1h; upload PUT: 15 min). Detalhe completo em
[DEPLOYMENT.md](docs/DEPLOYMENT.md#3-urls-assinadas-do-gcs-v4).

## Exceções de domínio

Nunca vazam detalhes internos — são tipos puros (`DomainErrorType`) traduzidos para
`ErrorResponse {status, error, message, path, timestamp}` só no
`GlobalExceptionHandler`. Ver [ARCHITECTURE.md §3](docs/ARCHITECTURE.md#exceções).
