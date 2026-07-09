# ADR-30 — 2FA via TOTP (Google Authenticator)

**Contexto:** Sem 2FA, acesso ao e-mail de qualquer usuário vira acesso à conta — bastava acionar o reset de senha. Com cadastro por aprovação admin, isso é pior do que parece: uma conta comprometida pode ser usada por alguém que nunca deveria ter entrado. Adicionamos TOTP antes de habilitar o reset de senha.

**Decisão:** Implementar 2FA via TOTP usando dev.samstevens.totp:totp Funciona com Google Authenticator, Authy e qualquer app TOTP compatível com RFC 6238. O reset de senha exige código TOTP quando o usuário tem 2FA habilitado; sem 2FA, aceita só o token do e-mail.

**Alternativa descartada:** 2FA via e-mail. Descartada porque o e-mail já é o canal do reset de senha, usar o mesmo canal como segundo fator não adiciona nada. TOTP é independente do e-mail e funciona offline.

**Fluxo de login com 2FA:** O login retorna um tempToken (JWT de 5 min com claim purpose:2fa) quando o usuário tem TOTP habilitado. O frontend exibe campo de código e envia POST /auth/2fa/authenticate { tempToken, totpCode }. Só depois da validação do TOTP o backend emite accessToken + refreshToken.

**Tradeoff aceito:** Se o usuário perder o dispositivo autenticador, não existe recovery code, vai precisar pedir ao admin pra desabilitar o 2FA manualmente no banco. Aceitável pro volume atual de usuários.
