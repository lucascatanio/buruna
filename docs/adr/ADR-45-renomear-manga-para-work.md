# ADR-45 — Renomear Manga para Work em código, banco e rotas

**Status:** Aceita

**Contexto:** O agregado `Manga` já era usado para livros (`format = LIVRO`, V19), e o
redesenho de capítulos traz livros em PDF e EPUB por upload. O nome deixava de descrever o
que o agregado representa e confundia quem chega ao código: um livro era um "Manga" no
backend, na tabela `mangas` e na rota `/mangas`.

As alternativas eram manter `Manga`, renomear só o Java mantendo tabelas e rotas
(`@Table(name = "mangas")`), ou renomear em tudo.

**Decisão:** renomear em tudo para `Work` (obra):

- **Código:** o contexto `com.buruna.manga` vira `com.buruna.work`, e todo identificador
  `Manga`/`manga` vira `Work`/`work` (`Work`, `WorkFormat`, `PromoteWorkUseCase`,
  `workId`...). No frontend, `mangaApi.ts` → `workApi.ts`, `types/manga.ts` →
  `types/work.ts` e as páginas equivalentes.
- **Banco (V27):** `mangas` → `works`, `manga_tags` → `work_tags`, colunas `manga_id` →
  `work_id`, tipos enum `manga_*` → `work_*`, e constraints e índices com "manga" no nome.
- **API:** `/mangas` → `/works`, `/my/mangas` → `/my/works`, `/mangas/{id}/rating` →
  `/works/{id}/rating`. Os campos JSON seguem os nomes novos (`mangaId` → `workId`).
- **Site:** `/mangas/novo` → `/obras/nova` e `/mangas/:id/editar` → `/obras/:id/editar`,
  com as rotas antigas redirecionando. As URLs públicas (`/biblioteca/:slug`, `/colecao`,
  `/leitor/:id`) não tinham "manga" e não mudam.

O que **não** muda: os valores do enum de formato (`MANGA` continua sendo um formato), os
textos de produto em português ("mangá" quando é o tipo de conteúdo), os e-mails enviados
ao usuário, as migrations V1–V26 e os ADRs anteriores, que registram as decisões com os
nomes da época.

**Transição no deploy (janela curta, aceita):**

- O Flyway renomeia o banco no startup da revisão nova. Até o tráfego passar para ela, a
  revisão antiga do Cloud Run falha nas queries que tocam os nomes antigos, por alguns
  segundos. View de compatibilidade não resolve: ela cobre tabela renomeada, mas não coluna
  renomeada dentro de outra tabela (`ratings.manga_id`).
- O backend sobe antes do frontend, e abas abertas continuam com o JS antigo até
  recarregar. Por isso as rotas `/mangas...` continuam aceitas **por uma versão**, como
  alias nos controllers. Os campos JSON mudam de uma vez; uma aba antiga pode falhar até
  ser recarregada.
- O deploy vai para um horário de pouco uso.

**Tradeoff:** é um diff grande, com uma janela curta de erro no deploy, para um ganho de
legibilidade. A alternativa de duas versões sem erro nenhum (colunas duplicadas com
trigger, JSON com os dois nomes) dobraria o trabalho e o risco para evitar segundos de
falha num projeto com dezenas de usuários.

**Pendente:** remover os aliases `/mangas` dos controllers (`WorkController`,
`PrivateWorkController`, `VolumeController`, `RatingController`) na versão seguinte.
