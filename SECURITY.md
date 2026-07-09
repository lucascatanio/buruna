# Segurança

## Reportando uma vulnerabilidade

Encontrou um problema de segurança? Não abra uma issue pública. Envie um e-mail para
o mantenedor (ver `ADMIN_EMAIL` configurado no projeto, ou contato no perfil do
GitHub) descrevendo o problema e, se possível, um passo a passo para reproduzir.
Responderemos e coordenaremos a correção antes de qualquer divulgação pública.

## Ciclo de vida do JWT + Refresh Token

```
Login (sem 2FA)
  ├── accessToken  (JWT, assinado com JWT_SECRET)
  │   expira em:  JWT_EXPIRATION segundos (padrão: 3600 = 1h)
  │   contém:     userId, role
  │   usado em:   Authorization: Bearer <token>
  │
  └── refreshToken (UUID aleatório)
      expira em:  REFRESH_TOKEN_EXPIRATION segundos (padrão: 604800 = 7 dias)
      armazenado: tabela refresh_tokens (token + user_id + expires_at)
      usado em:   POST /auth/refresh para obter novo accessToken
      invalidado: no logout OU ao ser usado (rotação de token)

Login (com 2FA)
  POST /auth/login { email, password }
  → se totpEnabled == true: retorna { requires2FA: true, tempToken }
    tempToken: JWT com claim "purpose":"2fa", expira em 5 min
  → POST /auth/2fa/authenticate { tempToken, totpCode }
  → valida tempToken + código TOTP
  → retorna accessToken + refreshToken normalmente

Refresh (com rotação)
  POST /auth/refresh { refreshToken }
  → backend busca token no banco, valida expires_at
  → deleta token antigo, gera e persiste um novo
  → retorna { accessToken, refreshToken, expiresIn }

Logout
  POST /auth/logout { refreshToken }
  → backend deleta token do banco
  → frontend limpa tokens armazenados
```

Cada chamada a `/auth/refresh` invalida o token usado e emite um novo — se um refresh
token vazar, a janela de exploração cai de 7 dias para um único ciclo. Detalhe e
tradeoff: [ADR-28](docs/adr/ADR-28-refresh-token-rotation.md).

> ⚠️ **Issue de segurança conhecida:** o botão de logout do frontend só limpa os
> tokens localmente — não chama `POST /auth/logout`, então o refresh token permanece
> válido no servidor até expirar. Também não há UI para `DELETE /auth/account`. Ver
> [`docs/BACKLOG.md`](docs/BACKLOG.md).

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
