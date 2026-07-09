# ADR-29 — Resend API para envio de e-mail com domínio próprio

**Contexto:** O envio de e-mails usava Gmail SMTP com App Password. Além de frágil (o Google pode revogar a qualquer momento), o remetente era um `@gmail.com` — o que prejudica deliverability e não passa credibilidade. Com o domínio `buruna.com.br` já registrado, fazia sentido ter e-mails saindo de `@buruna.com.br`.

**Decisão:** Migrar para a API HTTP do Resend com domínio `buruna.com.br` configurado com DKIM, SPF e DMARC. Criamos uma interface `EmailSender` e a implementação `ResendEmailSender` que faz `POST https://api.resend.com/emails`. Se a API key estiver vazia, o envio é ignorado com log — permite rodar localmente sem configuração.

**Justificativa:** O Resend tem free tier de 100 e-mails/dia (suficiente por anos no volume atual), API simples sem SDK pesado, e o setup de DNS garante que os e-mails não caiam em spam. A interface `EmailSender` existe porque agora há justificativa real para a abstração — se amanhã o Resend mudar pricing ou cair, trocar o provider é criar uma classe nova e trocar o `@Component`.

**Tradeoff aceito:** Dependência de serviço externo para envio de e-mails. Se o Resend sair do ar, os e-mails ficam silenciosamente perdidos (o `@Async` não tem retry). Pro volume atual, isso é aceitável — nenhum e-mail do Burūna é crítico a ponto de exigir fila com dead letter.
