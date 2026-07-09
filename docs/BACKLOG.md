# Backlog

Achados durante a refatoração (Epics 0-6), fora do escopo das issues executadas —
não fazer sem issue própria.

## Rename pendente `controller/` → `web/`, `service/` → `application/`

`manga/controller/` (MangaController, PrivateMangaController, VolumeController) convive
com `manga/web/` (só TagController); `admin/controller/` + `admin/service/` nunca foram
renomeados. Investigado no [6.3]: sem duplicação de rota, tudo vivo e chamado pelo
frontend/testes — é inconsistência de nomenclatura de migração incompleta, não código
morto. Vale uma issue de rename puro quando o padrão `web/` + `application/` for
revisitado.

## Logout não revoga refresh token no servidor

`POST /auth/logout` e `DELETE /auth/account` existem e têm teste no backend, mas o
botão de logout do frontend (`AppLayout.tsx`, `AdminLayout.tsx`) só chama `clearAuth()`
local — nunca chama `/auth/logout`. Refresh token permanece válido no servidor após
logout. Também não há UI para deletar conta. Achado no [6.3], investigação read-only;
precisa de issue de segurança própria (ligar o botão ao endpoint existente, avaliar
revogação de todos os refresh tokens do usuário).

## Adicionar `MangaSubmissionStatus.APPROVED`

O fluxo de submissão é assimétrico — `REJECTED` é um estado persistido
(`Manga.reject`), mas a aprovação não tem estado próprio: `Manga.approve` só marca
`isPublic=true` e zera `submissionStatus`, saindo do fluxo de submissão sem deixar
rastro no enum. Confunde quem lê o domínio (ver `docs/glossario-dominio.md` §3). Achado
na Fase 4 (docs vivas), investigação read-only — precisa de issue própria. Tornar
simétrico: enum `{PENDING, APPROVED, REJECTED}` + migration (nova coluna/valor) +
ajuste em `ReviewSubmissionUseCase`/`Manga.approve` + teste de regressão. Prioridade:
clareza de domínio.
