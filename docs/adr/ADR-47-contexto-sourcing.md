# ADR-47 — Contexto sourcing para importação e acompanhamento

**Status:** Aceita

**Contexto:** Importar do MangaDex ([ADR-46](ADR-46-mangadex-como-fonte-externa.md)) traz
conceitos que não são da obra: a obra acompanhada numa fonte, as versões de cada capítulo
(uma por grupo), qual delas entrou, falhas e um executor por vez. Pôr isso no `work` misturaria
o catálogo com o estado de uma integração.

**Decisão:**

- Contexto novo `com.buruna.sourcing`, no mesmo padrão `domain/ application/ persistence/ web/`,
  guardado pelo ArchUnit. Fala com o `work` só por use case público:
  `CreateImportedWorkUseCase`, `EnsureTagsUseCase`, `RegisterChapterUseCase`
  (`RegisterChapterCommand.importedPages`), `PublishImportedChapterUseCase` e
  `FindPublicWorkUseCase`. Os comandos que cruzam a fronteira usam tipos simples (nome do
  enum em texto), porque `sourcing.application` não pode importar `work.domain`.
- Agregados:
  - `TrackedWork`: obra do Burūna acompanhada (por id), idiomas e grupo preferido de cada um,
    estado da última listagem;
  - `SourceChapter`: uma versão de capítulo na fonte (PENDING, READY, FAILED, IGNORED) e o id
    do capítulo criado;
  - `SourceLease`: uma linha por fonte; quem segura baixa dela.
- Escolha de versão (`ChapterSelection`): a do grupo preferido; sem ela, a publicada mais
  recentemente. Capítulo sem número sempre entra. Número que já tem versão baixada não troca
  de versão sozinho.
- Unicidade sem UNIQUE ([ADR-53](ADR-53-regra-de-negocio-na-aplicacao-sem-unique.md)): um
  acompanhamento por obra da fonte, sob lock na linha do lease; uma versão por id externo,
  sob lock na linha da obra acompanhada.
- Tags do MangaDex são traduzidas por uma tabela fixa (`SourceTagTranslations`) e criadas
  quando faltam, na categoria do grupo delas (gênero, tema, formato, aviso de conteúdo,
  demografia).

**Tradeoff:** mais um contexto e alguns use cases públicos no `work` só para a importação.
Em troca, o `work` não sabe que o MangaDex existe, e o sync (E7) cresce dentro do `sourcing`.
