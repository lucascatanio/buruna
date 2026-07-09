# ADR-03 — @Async e @Scheduled internos

**Contexto:** O projeto precisa de envio de e-mail assíncrono (cadastro, aprovação, inatividade) e de um job diário de inatividade.

**Decisão:** Usar `@Async` do Spring para e-mails e `@Scheduled` para o job, em vez de Kafka, RabbitMQ ou outra fila.

**Por quê:** O volume de eventos assíncronos é baixíssimo: dezenas de e-mails por semana, um job por dia. Meter um message broker no meio é um serviço a mais pra operar, monitorar e pagar. O `@Async` resolve sem infraestrutura adicional. O Cloud Scheduler entra como trigger externo redundante pro job, cobrindo o caso em que o container escala pra zero e o `@Scheduled` interno não chega a rodar.

**Tradeoff:** Se o `@Async` falhar (exceção no envio de e-mail), não tem retry automático nem dead letter queue. Com o volume atual, ok. E-mails perdidos são raros e não travam nada.
