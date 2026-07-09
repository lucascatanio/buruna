# ADR-39 — Cross-context read via use case público; proibido JOIN/SQL em tabelas de outro contexto

**Status:** Aceita (Epic 2 da refatoração)

**Contexto:** Durante a migração do contexto `reading`, a query `findLatestByUserIdAndMangaId` foi implementada com `@Query(nativeQuery=true)` que fazia JOIN na tabela `volumes` — tabela pertencente ao contexto `manga`. Isso violava a regra de dependência (ADR-31 §2.3): um contexto não deve acessar as estruturas internas de outro. A violação passou despercebida no primeiro momento porque SQL nativo não cria importações Java detectáveis pelo ArchUnit — é um acoplamento invisível ao guard de arquitetura.

**Decisão:**

1. **Cross-context read passa exclusivamente por use case público.** Quando o contexto A precisa de dados do contexto B, A deve chamar um método publicado em `B.application`. Proibido acessar tabelas, repositórios ou entidades internas de B diretamente — mesmo via SQL nativo ou JPQL com referência implícita.

2. **`@Query(nativeQuery=true)` é proibido nas camadas `persistence` dos contextos migrados**, salvo quando a query acessa exclusivamente as próprias tabelas do contexto. Na prática, evitar por completo: se a query pode ser expressa com Spring Data derived methods ou JPQL interno, prefira essas formas. Native SQL fica reservado para otimizações de performance intracontexto, documentadas e revisadas caso a caso.

3. **Caso concreto corrigido:** `ReadingProgressRepository.findLatestByUserIdAndMangaId` (JOIN em `volumes`) foi removido. No lugar:
   - `manga.application.GetVolumeIdsByMangaUseCase.getVolumeIdsOrderedByNumberDesc(mangaId)` retorna os volume IDs do mangá na ordem correta — manga é dono dessa ordenação.
   - `reading.persistence.ReadingProgressRepository.findByUserIdAndVolumeIdIn` retorna os progressos existentes.
   - `reading.application.ReadingService.getProgress` orquestra as duas chamadas e acha o progresso no volume de maior número.

**Por quê:** SQL nativo (e JPQL com referência a entidades de outro contexto) cria acoplamento estrutural não detectável por análise de bytecode. Um contribuidor que renomeia a tabela `volumes` no contexto manga não consegue localizar os impactos no contexto `reading` por grep de imports Java — a quebra só aparece em runtime. Use cases públicos tornam a dependência explícita, nomeada e rastreável.

**Tradeoff:** A solução de dois passos (fetch de IDs + fetch de progresso) emite duas queries onde antes havia uma. Para um mangá com centenas de volumes, o IN clause pode crescer. Na prática, coleções de mangás raramente ultrapassam dezenas de volumes, e a leitura com progresso é uma operação pouco frequente e single-user — o custo é negligenciável. Se surgir um caso crítico de performance, a mitigação correta é um método especializado em `manga.application` (ex.: um use case que retorna somente o volumeId com maior número), não voltar ao SQL nativo cross-contexto.

**Guard de arquitetura:**

O ArchUnit detecta `@Query(nativeQuery=true)` nas camadas `persistence` de contextos migrados e falha o build (ver `ArchitectureTest.persistenceLayer_shouldNotUseNativeQueries`). Esse guard cobre a violação mais provável: native SQL que faz JOIN em tabela de outro contexto.

**O que nenhum guard cobre — review-only:**

- **JPQL com nome de entidade de outro contexto** (ex.: `FROM Volume v` em repositório de `reading`): nomes de entidade JPQL são strings na anotação; não geram importação Java no bytecode, logo o guard de imports não os vê. O guard de native query também não os detecta. Essa forma de acoplamento é invisível ao ArchUnit e depende exclusivamente de revisão de código.
- O guard de imports (`domainAndApplication_shouldNotImportInternalsOfOtherContexts`) captura apenas referências ao *tipo* Java — tipo de retorno, parâmetro, uso da Criteria API — onde o compilador registra um import real no bytecode.

A regra é, portanto, garantida por dois mecanismos complementares: guard automático (native SQL) + revisão de código (JPQL cross-contexto e qualquer outra forma de acoplamento por string).
