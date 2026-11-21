# Contrato: o que da API fica idêntico

A migração não cria nem altera endpoint. Este arquivo lista o que o frontend, o Cloud
Scheduler e o Pub/Sub esperam do backend hoje, e que precisa continuar igual depois dela
(FR-009, FR-010, FR-011). A referência dos endpoints continua sendo o código e o Swagger
(desligado em produção).

## JSON das respostas (FR-009)

| Invariante | Hoje | Coberto por |
|---|---|---|
| Datas (`OffsetDateTime`/`Instant`) | string ISO-8601 com fuso, nunca número | caracterização (research R10) |
| `Duration` | string ISO-8601 (`PT2M`), nunca número | `use-jackson2-defaults` (R2) |
| Campo nulo sem `@JsonInclude` | aparece como `null` | caracterização |
| Campo nulo com `@JsonInclude(NON_NULL)` (`LoginResponse`) | some do JSON | caracterização |
| Campo desconhecido no corpo do pedido | ignorado, sem 400 | `use-jackson2-defaults` |
| Nomes de campo | camelCase, iguais aos de hoje | testes de integração existentes (`jsonPath`) |

## Erros (FR-009)

| Origem | Formato |
|---|---|
| `GlobalExceptionHandler` (domínio, validação, 404 de recurso) | `ErrorResponse {status, error, message, path, timestamp}` |
| 401 do `authenticationEntryPoint` (`sendError`) | JSON de erro padrão do Boot, com os mesmos campos de hoje (fixados pela caracterização) |
| Rota inexistente, método não permitido, corpo mal formado | mesmo status e mesmo formato de hoje, sem stack trace nem detalhe interno |

## Acesso e borda (FR-010, FR-011)

- A matriz de `SecurityConfig` (rotas públicas, autenticadas, `@PreAuthorize`) não muda. O
  `context-path` é `/api`.
- `/admin/jobs/**` exige `X-Job-Secret` válido e devolve 401/403 como hoje.
  `/internal/pubsub/**` exige o token OIDC do Pub/Sub.
- `/health` devolve `{"status":"UP"}`.
- O cookie de refresh continua `httpOnly`, `SameSite=Strict`, com `Secure` conforme
  `APP_AUTH_COOKIE_SECURE`, mesmo nome e mesmo caminho.
- O CORS libera só `APP_CORS_ALLOWED_ORIGIN`. O filtro `X-Proxy-Secret` continua ativo
  quando há segredo.
- Tokens JWT emitidos antes do deploy continuam válidos: mesma chave, mesmo algoritmo, mesma
  biblioteca (jjwt 0.12.6).
