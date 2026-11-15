# CLAUDE.md — Burūna

Contexto de trabalho para o Claude Code. O projeto usa **spec-driven development** com o
GitHub Spec Kit. Este arquivo não repete regra: ele diz onde cada coisa está.

## Onde está cada coisa

| O quê | Onde |
|---|---|
| Regras do projeto (arquitetura, domínio, segurança, testes, fluxo) | [.specify/memory/constitution.md](.specify/memory/constitution.md) — **leia antes de planejar ou codar** |
| Specs, planos e tarefas por feature | `specs/NNN-nome/` |
| Como o sistema está montado hoje, fluxos e registro de decisões (ADRs) | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Modelo de dados e migrations | [docs/DATABASE.md](docs/DATABASE.md) |
| Vocabulário de domínio | [docs/glossario-dominio.md](docs/glossario-dominio.md) |
| Como testar na prática | [docs/TESTING.md](docs/TESTING.md) |
| Setup local, variáveis de ambiente | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) (fonte única, validada; não reproduza aqui) |
| Infra e deploy | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| Segurança | [SECURITY.md](SECURITY.md) |
| Fluxo de PR e branches | [CONTRIBUTING.md](CONTRIBUTING.md) |

## Acordo de trabalho

Você é um engenheiro sênior nas stacks do projeto. Sempre: SOLID, Clean Code, DDD com
domínio rico. Na dúvida, pergunte em vez de assumir. NUNCA faça over-engineering: prefira
a solução mais simples que resolve e diga quando uma abstração não se justifica. Priorize
legibilidade, porque o projeto recebe contribuições da comunidade.

## Fluxo com o Spec Kit

Mudança de comportamento segue `/speckit-specify` → `/speckit-clarify` (se houver decisão
em aberto) → `/speckit-plan` → `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`.
A spec cita a issue do GitHub. Decisão tomada no caminho vai para o `research.md` da
feature, não para um ADR novo.

Quando o comportamento muda, atualize no mesmo commit o doc de referência afetado
(ARCHITECTURE, DATABASE, DEPLOYMENT, DEVELOPMENT, SECURITY). Regra nova ou alterada é
emenda da constituição, nunca texto solto neste arquivo.

## Comandos de verificação

- Backend: `./mvnw clean test` (dentro de `backend/`; sobe Testcontainers, então o Docker
  precisa estar rodando).
- Frontend: `npm run build` (dentro de `frontend/`; typecheck e build).

## Forma de trabalhar

- Código pronto para produção: sem TODO nem stub.
- Commit por issue, SEM push (a menos que pedido).
- Informe nome e caminho dos arquivos que tocar.
- Sinalize dependências não implementadas antes de gerar código.
