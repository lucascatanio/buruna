# ADR-22 — Cloud Run separado para frontend e backend

**Contexto:** O deploy poderia ser: (a) dois serviços Cloud Run separados (frontend + backend) ou (b) um GCE único rodando Docker Compose com ambos.

**Decisão:** Cloud Run separado pra cada um.

**Por quê:** Cloud Run escala pra zero. Quando ninguém acessa, custo zero. Com GCE, o VM fica ligado 24/7 mesmo sem tráfego. Pra um projeto com picos esporádicos de uso, o modelo serverless do Cloud Run sai muito mais barato. Fora isso, deploys independentes permitem atualizar frontend e backend separadamente sem downtime (Cloud Run faz blue-green deployment automático por revisão).

**Tradeoff:** Cold starts no Cloud Run (~2–5s no primeiro request depois de escalar de zero). Mitigado com UptimeRobot fazendo ping a cada 5 minutos, mantendo pelo menos uma instância quente. O PostgreSQL continua em GCE (ver ADR-23) porque precisa de persistência em disco.
