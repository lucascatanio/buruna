# ADR-33 — Exceções de domínio puras; tradução para HTTP só na borda

**Status:** Aceita (Fase 2 da refatoração)
**Contexto:** Hoje `DomainException(HttpStatus, msg)` carrega status HTTP dentro da camada de negócio; 6 services referenciam `HttpStatus` (PrivateMangaService: 11 ocorrências). O domínio "sabe" que existe HTTP — acoplamento da regra à camada web, e mais um motivo pelo qual regra não é testável isolada.

**Decisão:** Exceções de domínio passam a ser **puras** (sem `HttpStatus`), específicas por caso (`MangaAlreadyPublicException`, `DuplicateVolumeException`, `NotAllowedToPublishException`…) estendendo um `DomainException` sem dependência web. O **único** lugar que mapeia exceção → status HTTP é o `GlobalExceptionHandler` (`@RestControllerAdvice`) em `shared/web`, via `@ExceptionHandler` por tipo (ou um registro tipo→status). O formato `ErrorResponse` padronizado é preservado.

**Por quê:** Tira HTTP do domínio (regra testável sem web), centraliza a política de status num lugar revisável, e dá exceções nomeadas por intenção — mais legível para o contribuidor do que `throw new DomainException(HttpStatus.CONFLICT, "...")` repetido. Mantém o tratamento de erro centralizado que já é um ponto forte do projeto.

**Tradeoff:** Mais classes de exceção (uma por caso) e a necessidade de registrar cada uma no handler. Aceitável: são classes triviais e o mapeamento fica explícito e auditável. Exceções genéricas remanescentes (validação, integridade) continuam tratadas como hoje.
