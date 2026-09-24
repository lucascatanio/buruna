# ADR-03 — @Async e @Scheduled internos

**Contexto:** O projeto precisa de envio de e-mail assíncrono (cadastro, aprovação, inatividade) e de um job diário de inatividade.

**Decisão:** Usar `@Async` do Spring para e-mails e `@Scheduled` para o job, em vez de Kafka, RabbitMQ ou outra fila.

**Por quê:** O volume de eventos assíncronos é baixíssimo: dezenas de e-mails por semana, um job por dia. Meter um message broker no meio é um serviço a mais pra operar, monitorar e pagar. O `@Async` resolve sem infraestrutura adicional. O Cloud Scheduler entra como trigger externo redundante pro job, cobrindo o caso em que o container escala pra zero e o `@Scheduled` interno não chega a rodar.

**Tradeoff:** Se o `@Async` falhar (exceção no envio de e-mail), não tem retry automático nem dead letter queue. Com o volume atual, ok. E-mails perdidos são raros e não travam nada.

**Atualização (2026-09-24):** os e-mails deixaram de ser `@Async`. No Cloud Run com
`cpu-throttling`, a thread do `@Async` rodava depois da resposta HTTP, quando a CPU da
instância já tinha sido cortada, e a chamada ao Resend parava até dar timeout: 62 de 84
e-mails falharam em 30 dias, contra a premissa de que "e-mails perdidos são raros". O envio
agora é síncrono, dentro da requisição, com timeout de 5 s, e as notificações de admin vão
num único lote do Resend (PR #11). O `@Scheduled` e o Cloud Scheduler seguem como descritos.
