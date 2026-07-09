## O que muda e por quê

<!-- Resumo curto do problema resolvido/feature adicionada. Foque no "porquê". -->

## Como testar

<!-- Passos para validar manualmente, se aplicável. -->

## Checklist

- [ ] `./mvnw clean test` verde (`backend/`) — inclui `ArchitectureTest`
- [ ] `npm run build` verde (`frontend/`, se a mudança tocar o front)
- [ ] Mudança de comportamento tem teste automatizado equivalente
- [ ] Nenhum `@Query(nativeQuery = true)` nem `JOIN` cruzando bounded contexts
- [ ] Nenhuma entidade JPA retornada por use case (sempre DTO)
- [ ] Sem segredo hardcoded (variáveis de ambiente)
- [ ] PR focado numa mudança — não mistura bounded contexts sem necessidade

Ver [CONTRIBUTING.md](../CONTRIBUTING.md) para o checklist completo e regras de
arquitetura.
