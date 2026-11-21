# Data Model: Migrar o backend para o Spring Boot 4

A feature não cria, altera nem remove entidade, coluna, índice ou enum. Não há migration
nova (FR-012). O esquema de [docs/DATABASE.md](../../docs/DATABASE.md) continua valendo
como está.

O que precisa continuar igual, e como é conferido:

| Ponto | Por que pode mudar com a troca | Conferência |
|---|---|---|
| Mapeamento JPA ↔ esquema | Hibernate 6.6 → 7.4 pode inferir tipo diferente | `ddl-auto: validate` no startup dos testes de integração contra o Postgres do Testcontainers ([research R6](research.md#r6-hibernate-74-e-jpa-32)) |
| Enums nativos do Postgres (`@JdbcTypeCode(NAMED_ENUM)`) | mudança no tratamento de tipo nomeado | os mesmos testes, que gravam e leem cada agregado |
| Linhas de `source_leases` criadas pela migration | `merge` de id atribuído no Hibernate 7 | `SourcingIntegrationTest` e `SourcingSyncIntegrationTest` (pegam e renovam o lease) |
| Hash de refresh token e de reset de senha | nenhum, porque o algoritmo é do projeto | `IdentityIntegrationTest` |
| Histórico do Flyway | o Flyway 12 lê o `flyway_schema_history` de hoje | a suíte sobe com o banco vazio e aplica tudo. Em produção, o startup não pode aplicar nada novo ([quickstart](quickstart.md), passo P2) |
