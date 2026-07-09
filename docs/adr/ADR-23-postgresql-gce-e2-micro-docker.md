# ADR-23 — PostgreSQL no GCE e2-micro com Docker

**Contexto:** O banco precisa de persistência e disponibilidade. As opções eram Cloud SQL (managed) ou PostgreSQL self-hosted em GCE.

**Decisão:** PostgreSQL 16 em container Docker rodando numa instância GCE e2-micro (free tier permanente do GCP).

**Por quê:** Cloud SQL db-f1-micro custa ~$10/mês. O GCE e2-micro é de graça no free tier permanente do GCP. Pra um projeto com dezenas de usuários e queries simples, a performance do e2-micro dá conta. O PostgreSQL em Docker é fácil de configurar, fazer backup (pg_dump via cron) e migrar.

**Tradeoff:** Sem alta disponibilidade (single point of failure), sem backups automáticos gerenciados, sem patching automático do PostgreSQL. O dev precisa gerenciar backups e atualizações na mão. Pro tamanho do projeto, o risco é aceitável. Recovery de um pg_dump leva minutos.
