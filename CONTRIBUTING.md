# Contribuindo com o Burūna

Obrigado por considerar contribuir. As regras do projeto moram num lugar só, a
[constituição](.specify/memory/constitution.md).

## Contribuindo de fora do time

Você **não precisa** usar o Spec Kit nem escrever spec. Abra uma issue (ou pegue uma
aberta), faça a mudança numa branch a partir da `dev` e mande o PR para a `dev`, seguindo o
[fluxo de PR](#fluxo-de-pr) e o [checklist](#checklist-antes-de-abrir-o-pr). A revisão
confere as regras de arquitetura no código e, se a mudança pedir uma spec, quem é do time a
escreve. O check `Spec` passa sozinho no seu PR.

Se quiser usar o fluxo spec-driven mesmo assim, a seção [Spec antes do
código](#spec-antes-do-código) explica como. É opcional para você.

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
- O workflow `Spec` (`.github/workflows/spec-check.yml`) roda em todo PR para `dev`: código de
  aplicação sem pasta em `specs/` (e sem a label `sem-spec`), ou spec sem `validation.md`
  APROVADA, falha. PR de quem é de fora do time e do Dependabot passa direto. Para conferir
  antes do push: `scripts/check-spec.sh`.

## Spec antes do código

Obrigatório para o time do projeto (quem tem acesso de escrita ao repositório), opcional para
quem contribui de fora. O time usa **spec-driven development** com o
[GitHub Spec Kit](https://github.com/github/spec-kit): toda mudança de comportamento é
especificada antes de ser codada.

Para feature, bug com decisão em aberto ou qualquer mudança de comportamento:

Abra (ou escolha) uma issue e, com um agente compatível com o Spec Kit (o projeto vem
configurado para o Claude Code, em `.claude/skills/`), siga as quatro fases da
[constituição](.specify/memory/constitution.md#fluxo-de-desenvolvimento):

| Fase | Comandos | Sai daqui |
|---|---|---|
| Definir | `/speckit-specify` com a issue; `/speckit-clarify` se houver decisão em aberto | `spec.md` |
| Planejar | `/speckit-plan`, `/speckit-tasks`, `/speckit-analyze` | `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `tasks.md` |
| Construir | `/speckit-implement`, parando em cada checkpoint com os testes verdes; `/speckit-converge` até não sobrar lacuna | código e testes |
| Validar | `/validar-quickstart` | `validation.md` com resultado APROVADA |

A pasta `specs/NNN-nome/`, com o `validation.md`, entra no mesmo PR que o código.

Correção de uma linha, bump de dependência, mudança só em teste e ajuste de texto ou doc
dispensam spec; se o PR tocar código de aplicação, ponha a label `sem-spec`. Na dúvida,
escreva a spec. Sem agente, dá para preencher os templates de `.specify/templates/` à mão.

### Validação no navegador

O `/validar-quickstart` usa o [Playwright MCP](https://github.com/microsoft/playwright-mcp),
configurado em `.mcp.json`. Na primeira vez, o Claude Code pede para aprovar o servidor
(`/mcp`); ele usa o Chrome instalado na máquina. O app sobe com `scripts/dev-env.sh up`
(Postgres do docker compose, backend no profile `local` e Vite), que precisa do `.env` na
raiz, e `scripts/dev-env.sh admin` cria uma conta ADMIN local para o teste. O mesmo script
serve para subir o ambiente à mão.

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
- [ ] (Time do projeto) Mudança de comportamento tem a pasta `specs/NNN-nome/` no PR, o
      `plan.md` passou no *Constitution Check* e o `validation.md` está APROVADA (o CI confere).
- [ ] Mudança de comportamento tem teste automatizado equivalente (rede de
      segurança) — não abra PR de refatoração de fluxo sem cobertura do comportamento
      atual.
- [ ] Nenhum `@Query(nativeQuery = true)` nem `JOIN` cruzando bounded contexts.
- [ ] Nenhuma entidade JPA retornada por use case (sempre DTO).
- [ ] Sem segredo hardcoded (variáveis de ambiente).

## Dúvidas

Abra uma issue com a tag `question` ou consulte [docs/glossario-dominio.md](docs/glossario-dominio.md)
para vocabulário de domínio.
