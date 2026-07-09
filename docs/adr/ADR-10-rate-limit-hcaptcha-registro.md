# ADR-10 — Rate limit + hCaptcha no registro

**Contexto:** O endpoint de registro é público e precisa de proteção contra bots e abuso.

**Decisão:** Rate limit de 5 requests/hora por IP via `RateLimitFilter` (in-memory `ConcurrentHashMap`) combinado com validação de hCaptcha em `CaptchaService`. O frontend renderiza o widget `@hcaptcha/react-hcaptcha` e envia o token resolvido no campo `captchaToken`; o backend valida via `POST https://api.hcaptcha.com/siteverify`. Em desenvolvimento (sem `HCAPTCHA_SECRET`), a chamada à API é ignorada.

**Por quê:** O rate limit barra abuso de volume (DDoS no endpoint, spam de e-mails ao admin). O hCaptcha impede bots sofisticados que respeitam rate limits — a defesa certa contra automações que criariam uma conta a cada 12 minutos por IP.

**Tradeoff:** Adiciona fricção pra usuários legítimos e dependência de serviço externo (hCaptcha). O captcha expira após alguns minutos, mas o widget exibe reset automático via `onExpire`.
