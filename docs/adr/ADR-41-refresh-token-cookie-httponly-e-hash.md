# ADR-41 — Refresh token em cookie httpOnly + hash dos tokens de autenticação

**Status:** Aceita (correção de dois achados de baixa severidade da revisão de segurança de 2026-09-23)

**Contexto:** Dois problemas independentes na autenticação, ambos de exploração indireta
(exigem um vazamento prévio para virarem exploráveis), foram corrigidos juntos por
tocarem o mesmo fluxo:

1. **Tokens em claro no banco.** `refresh_tokens.token` e `password_reset_tokens.token`
   guardavam o valor exatamente como emitido. Um vazamento do banco (dump, backup mal
   protegido, injeção de leitura) dava a um atacante sessões prontas para usar e a
   capacidade de resetar a senha de qualquer usuário com token válido — sem precisar
   quebrar hash nenhum.
2. **Tokens em `localStorage`.** O frontend guardava `accessToken` e `refreshToken` via
   Zustand `persist` em `localStorage`. Qualquer XSS no app (uma dependência
   comprometida, um campo de input mal sanitizado) lia os dois com `localStorage.getItem`
   e exfiltrava a sessão inteira, incluindo o refresh token — que por padrão dura 7 dias.

**Decisão:**

1. **Hash SHA-256 dos tokens persistidos.** `identity.application.authentication.TokenHash`
   (utilitário estático, sem estado — não é serviço de domínio porque não depende de
   repositório nem porta externa) expõe `sha256Hex(String)`. `TokenService` passa a
   gravar o hash em `refresh_tokens.token` (create/rotate/delete/find) e `AccountService`
   grava o hash em `password_reset_tokens.token` (forgot/reset/reset-info). O valor em
   claro nunca é persistido — só existe em trânsito: no cookie (refresh) ou no link do
   e-mail (reset). `TokenService.IssuedRefreshToken` (record interno) carrega o valor
   bruto de volta para quem chamou (a application), sem que ele nunca toque a coluna do
   banco.
   - **Migration V22__hash_auth_tokens.sql:** `DELETE FROM refresh_tokens` e
     `DELETE FROM password_reset_tokens` — não há como hashear um valor que não temos
     mais depois do deploy, e a decisão foi não guardar o valor em claro nem
     temporariamente para migrar. Todo usuário loga de novo; todo link de reset de
     senha pendente expira sem efeito. `refresh_tokens.token` encolhe de `VARCHAR(512)`
     para `VARCHAR(64)` (hex de SHA-256 tem sempre 64 chars); `password_reset_tokens.token`
     já era `VARCHAR(64)` desde a V18 e não muda.

2. **Refresh token em cookie httpOnly; access token só em memória.** Login (sem 2FA),
   `POST /auth/2fa/authenticate` e `POST /auth/refresh` passam a gravar o cookie
   `buruna_refresh` via `ResponseCookie`:
   `HttpOnly; Secure (exceto local); SameSite=Strict; Path=/api/auth; Max-Age=<refresh-token-expiration>`.
   `refreshToken` sai do corpo JSON de `LoginResponse`/`TokenResponse` (`@JsonIgnore` no
   record component — a application ainda devolve o valor bruto para a `web` montar o
   cookie, só não serializa). `POST /auth/refresh` e `POST /auth/logout` passam a ler
   `@CookieValue(required = false)`; `RefreshRequest` foi removido. Cookie ausente em
   `/auth/refresh` é 401 (`InvalidTokenException`); logout sem cookie é um no-op
   idempotente que ainda limpa o cookie do lado do navegador. `app.auth.cookie-secure`
   (env `APP_AUTH_COOKIE_SECURE`, default `true`) é `false` só em
   `application-local.yml`, porque dev local roda em HTTP puro e um cookie `Secure`
   nunca voltaria do navegador nesse caso.
   - **Frontend:** `authStore` perde `persist` e o campo `refreshToken` — só
     `accessToken` (em memória) e `user`. Um novo campo `initialized` marca quando o
     bootstrap terminou. `src/lib/authBootstrap.ts` roda uma vez na montagem do `App`:
     chama `POST /auth/refresh` com `withCredentials` (sem corpo — o cookie já vai
     junto), popula `accessToken` em caso de sucesso ou limpa o estado em caso de falha,
     e sempre marca `initialized`. `ProtectedRoute` aguarda `initialized` antes de
     decidir redirecionar para `/login` — sem isso, um F5 mandaria todo usuário logado
     para a tela de login antes da resposta do bootstrap chegar. O interceptor de 401 em
     `src/lib/axios.ts` faz o mesmo refresh sem corpo. `logout.ts`/`identityApi.logout`
     não mandam mais corpo. A chave antiga `buruna-auth` do `persist` é removida do
     `localStorage` no bootstrap (limpeza de uma vez, para quem tinha sessão do formato
     anterior).

**Por que `SameSite=Strict` + `Path=/api/auth` dispensam token CSRF aqui:** um cookie
`SameSite=Strict` só é enviado pelo navegador em requisições cuja origem de navegação é
o próprio site — nunca em navegação cross-site nem em `fetch`/`XHR` disparado por outro
site, mesmo com `credentials: include`. Isso já neutraliza CSRF contra os endpoints que
usam esse cookie: um site malicioso não consegue forjar uma requisição que carregue
`buruna_refresh`. `Path=/api/auth` reduz ainda mais a superfície — o cookie não vaza para
nenhum outro endpoint da API mesmo em cenários de same-site com outra área da aplicação.
Um token CSRF dedicado protegeria contra o mesmo ataque que `SameSite=Strict` já bloqueia
sozinho; adicionar os dois seria a abstração redundante que o projeto pede para evitar.
A ressalva de sempre: navegadores muito antigos sem suporte a `SameSite` ficariam sem essa
proteção — risco aceito, coerente com o suporte a browser já assumido pelo resto do app
(React 19 + Vite).

**Tradeoff:** hash é unidirecional — não há forma de recuperar o valor em claro de um
token perdido (não é o objetivo; refresh/reset sempre reemitem). O cookie `httpOnly`
tira do JavaScript do frontend qualquer visibilidade sobre o refresh token — inclusive
para debugging local, que agora depende de inspecionar o cookie pelas DevTools em vez de
`localStorage`. `SameSite=Strict` é mais restritivo que `Lax`: um link para
`/reset-password?token=...` recebido por e-mail e aberto pelo usuário não carrega o
cookie de sessão de qualquer jeito (o fluxo de reset não depende do cookie de refresh),
então isso não quebra esse caso, mas qualquer fluxo futuro que dependa do cookie
persistindo entre um clique vindo de fora do site precisaria reavaliar essa escolha.
