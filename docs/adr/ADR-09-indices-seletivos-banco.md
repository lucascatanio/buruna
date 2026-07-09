# ADR-09 — Índices seletivos no banco

**Contexto:** PostgreSQL cria índices automaticamente para PKs e UNIQUEs, mas FKs e colunas usadas em WHERE/ORDER BY precisam de índices manuais.

**Decisão:** Criar índices só nas colunas com queries frequentes e alta cardinalidade (7 índices manuais em FKs de leitura pesada como `reading_progress.user_id`, `reading_history.volume_id`, etc.). Não indexar toda FK de forma automática.

**Por quê:** Cada índice tem custo de escrita (INSERT/UPDATE mais lentos) e de storage. Com um dataset pequeno, a maioria das queries é rápida mesmo sem índice. Criamos índices onde o query plan mostrou sequential scans em tabelas que crescem proporcionalmente ao uso (leitura, progresso, histórico). FKs em tabelas pequenas e raramente filtradas (como `manga_tags` com composite PK) não precisam de índice extra.

**Tradeoff:** Queries em colunas não indexadas podem ficar lentas se o dataset crescer de forma inesperada. Monitorar e adicionar índices conforme a necessidade.
