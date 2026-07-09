# Contribuindo com o Burūna

Obrigado por considerar contribuir. Este projeto está em Clean Architecture + DDD por
bounded context — as regras abaixo são um resumo; a fonte completa é
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) e [CLAUDE.md](CLAUDE.md).

## Setup de desenvolvimento

Pré-requisitos, comandos de setup local e variáveis de ambiente:
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Não duplicado aqui.

## Fluxo de PR

- Branch base: **`dev`** (não `main`). Abra sua branch a partir de `dev` e mande o PR
  de volta para `dev`. `main` reflete produção.
- Um commit por unidade lógica (não um commit gigante por PR nem um por arquivo).
  Mensagem no padrão `tipo(escopo): descrição` (`feat`, `fix`, `chore`, `docs`,
  `refactor` — ver `git log` para exemplos do projeto).
- Sem push direto em `dev`/`main` — sempre via PR.
- PRs pequenos e focados numa mudança. Se sua mudança cruza bounded context (ex.:
  `manga` + `identity`), considere se não deveria ser dois PRs.
- CI (`.github/workflows/ci.yml`) roda `./mvnw test` em todo PR para `dev`/`main` —
  inclui `ArchitectureTest`.

## Regras de arquitetura que todo PR precisa respeitar

Resumo — detalhes completos e exemplos em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md):

- **Clean Architecture por bounded context**: cada contexto (`identity`, `manga`,
  `reading`, `engagement`, `admin`) segue `domain/ → application/ → persistence/ → web/`.
  Não crie `service/` nem `repository/` como nome de pacote.
- **Cross-contexto só via `application` pública do outro contexto.** Nunca importe
  `domain`/`persistence` de outro contexto. Referências cross-contexto são por **UUID**
  (`actorId`, `ownerId`) — nunca a entidade de outro contexto.
- **Domínio rico, sem setter público.** Regra de negócio é método com invariante no
  agregado (`manga.promoteToPublic()`, `user.approve()`), não um setter chamado de
  fora.
- **Exceções de domínio são puras** (sem `HttpStatus`) — tradução para HTTP só no
  `GlobalExceptionHandler`. `LegacyHttpDomainException` é legado em remoção; não crie
  novos usos dela.
- **RBAC na borda** (`@PreAuthorize` no controller); **ownership** é verificada na
  `application` via `actorId`, nunca no domínio de outro contexto.
- **Proibido** `@Query(nativeQuery = true)` cross-contexto e `JOIN` entre tabelas de
  contextos diferentes — passe por um use case público ([ADR-39](docs/adr/ADR-39-cross-context-read-via-use-case.md)).

**Isso é garantido por teste, não por revisão manual:** `ArchitectureTest`
(`backend/src/test/java/com/buruna/architecture/ArchitectureTest.java`), com ArchUnit,
**falha o build** se uma dessas fronteiras for violada. Rodar `./mvnw clean test`
localmente antes de abrir o PR pega isso antes do CI.

## Rede de segurança antes de refatorar

Ao tocar um fluxo de comportamento existente (não só estrutura), garanta que existe
teste automatizado cobrindo o comportamento atual antes de mudar a implementação. Ver
[docs/TESTING.md](docs/TESTING.md).

## Checklist antes de abrir o PR

- [ ] `./mvnw clean test` verde (dentro de `backend/`) — inclui `ArchitectureTest`.
- [ ] `npm run build` verde (dentro de `frontend/`, se a mudança tocar o front).
- [ ] Mudança de comportamento tem teste automatizado equivalente (rede de
      segurança) — não abra PR de refatoração de fluxo sem cobertura do comportamento
      atual.
- [ ] Nenhum `@Query(nativeQuery = true)` nem `JOIN` cruzando bounded contexts.
- [ ] Nenhuma entidade JPA retornada por use case (sempre DTO).
- [ ] Sem segredo hardcoded (variáveis de ambiente).

## Dúvidas

Abra uma issue com a tag `question` ou consulte [docs/glossario-dominio.md](docs/glossario-dominio.md)
para vocabulário de domínio.
