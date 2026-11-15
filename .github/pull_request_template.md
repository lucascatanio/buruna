## O que muda e por quê

<!-- Issue: #NNN · Spec: specs/NNN-nome/ (obrigatória para mudança de comportamento, ver CONTRIBUTING.md) · Validação: specs/NNN-nome/validation.md -->

<!-- Resumo curto do problema resolvido/feature adicionada. Foque no "porquê". -->

## Como testar

<!-- Passos para validar manualmente, se aplicável. -->

## Checklist

- [ ] `./mvnw clean test` verde (`backend/`) — inclui `ArchitectureTest`
- [ ] `npm run build` verde (`frontend/`, se a mudança tocar o front)
- [ ] Mudança de comportamento tem spec em `specs/` e o `plan.md` passou no *Constitution Check* (sem spec: label `sem-spec`)
- [ ] `validation.md` da spec com resultado APROVADA (`/validar-quickstart`)
- [ ] Mudança de comportamento tem teste automatizado equivalente
- [ ] Doc de referência atualizado se o fluxo, o schema, o deploy ou a segurança mudou
- [ ] Nenhum `@Query(nativeQuery = true)` nem `JOIN` cruzando bounded contexts
- [ ] Nenhuma entidade JPA retornada por use case (sempre DTO)
- [ ] Sem segredo hardcoded (variáveis de ambiente)
- [ ] PR focado numa mudança — não mistura bounded contexts sem necessidade

Regras: [constituição](../.specify/memory/constitution.md). Fluxo: [CONTRIBUTING.md](../CONTRIBUTING.md).
