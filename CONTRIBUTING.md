# Contribuindo com o Burūna

Obrigado por considerar contribuir. O Burūna usa **spec-driven development** com o
[GitHub Spec Kit](https://github.com/github/spec-kit): toda mudança de comportamento é
especificada antes de ser codada, e as regras do projeto moram num lugar só, a
[constituição](.specify/memory/constitution.md).

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
- PR de feature para `dev` entra com **squash**: vira um commit só na `dev`, com o título do
  PR como mensagem. Por isso o título segue o mesmo padrão `tipo(escopo): descrição`.
  Para trazer novidades da `dev` para a sua branch, use rebase ("Update branch" →
  "Update with rebase"), não merge. PRs entre `dev` e `main` (release e sincronização)
  usam merge commit, nunca squash — ver [DEPLOYMENT.md](docs/DEPLOYMENT.md) §6.
- PRs pequenos e focados numa mudança. Se sua mudança cruza bounded context (ex.:
  `work` + `identity`), considere se não deveria ser dois PRs.
- CI (`.github/workflows/ci.yml`) roda `./mvnw test` em todo PR para `dev`/`main` —
  inclui `ArchitectureTest`.

## Spec antes do código

Para feature, bug com decisão em aberto ou qualquer mudança de comportamento:

1. Abra (ou escolha) uma issue.
2. Com um agente compatível com o Spec Kit (o projeto vem configurado para o Claude Code,
   em `.claude/skills/`), rode na ordem:
   - `/speckit-specify` com a issue: gera `specs/NNN-nome/spec.md` (o quê e o porquê);
   - `/speckit-clarify`, se a spec tiver decisões em aberto;
   - `/speckit-plan`: gera `plan.md`, `research.md`, `data-model.md` e `contracts/`, e
     confere o plano contra a constituição;
   - `/speckit-tasks` e `/speckit-analyze`: lista de tarefas e checagem de consistência;
   - `/speckit-implement`.
3. A pasta `specs/NNN-nome/` entra no mesmo PR que o código.

Correção de uma linha, bump de dependência e ajuste de texto ou doc dispensam spec. Na
dúvida, escreva. Sem agente, dá para preencher os templates de `.specify/templates/` à mão.

Para instalar a CLI (`specify`), que só é necessária para atualizar os templates:
`uv tool install specify-cli --from git+https://github.com/github/spec-kit.git@v1.4.0`.

## Regras de arquitetura

Estão na [constituição](.specify/memory/constitution.md): camadas por bounded context,
fronteiras entre contextos, domínio rico, segurança, testes, simplicidade e trabalho
longo fora da requisição. O [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) mostra como o
sistema está montado hoje e resume as decisões antigas (ADRs) no §7.

**Boa parte disso é garantida por teste, não por revisão manual:** `ArchitectureTest`
(`backend/src/test/java/com/buruna/architecture/ArchitectureTest.java`), com ArchUnit,
**falha o build** se uma fronteira for violada. Rodar `./mvnw clean test` localmente antes
de abrir o PR pega isso antes do CI.

## Checklist antes de abrir o PR

- [ ] `./mvnw clean test` verde (dentro de `backend/`) — inclui `ArchitectureTest`.
- [ ] `npm run build` verde (dentro de `frontend/`, se a mudança tocar o front).
- [ ] Mudança de comportamento tem a pasta `specs/NNN-nome/` no PR, e o `plan.md` passou
      no *Constitution Check*.
- [ ] Mudança de comportamento tem teste automatizado equivalente (rede de
      segurança) — não abra PR de refatoração de fluxo sem cobertura do comportamento
      atual.
- [ ] Nenhum `@Query(nativeQuery = true)` nem `JOIN` cruzando bounded contexts.
- [ ] Nenhuma entidade JPA retornada por use case (sempre DTO).
- [ ] Sem segredo hardcoded (variáveis de ambiente).

## Dúvidas

Abra uma issue com a tag `question` ou consulte [docs/glossario-dominio.md](docs/glossario-dominio.md)
para vocabulário de domínio.
