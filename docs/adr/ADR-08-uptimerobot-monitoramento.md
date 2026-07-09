# ADR-08 — UptimeRobot para monitoramento

**Contexto:** Precisa de alerta quando o site cai.

**Decisão:** UptimeRobot (free tier) com check HTTP a cada 5 minutos e alerta por e-mail.

**Por quê:** Custo zero, configura em 2 minutos. A alternativa seria Cloud Monitoring, Prometheus ou algo do tipo, que adiciona complexidade e possivelmente custo pra resolver um problema que o UptimeRobot já resolve. Pra um projeto solo, saber que o site caiu em até 5 minutos é o bastante.

**Tradeoff:** Sem métricas de performance, sem alertas de latência, sem dashboards. Se o projeto crescer, migrar pra algo mais completo.
