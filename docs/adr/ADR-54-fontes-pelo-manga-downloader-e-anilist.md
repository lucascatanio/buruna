# ADR-54 — Fontes pelo manga-downloader, com metadados do AniList

**Status:** Aceita

**Contexto:** O `sourcing` só conhece o MangaDex ([ADR-47](ADR-47-contexto-sourcing.md)). A porta
`ExternalSource` (`shared/source`) junta duas coisas: os metadados da obra (busca, capa, sinopse,
tags) e os capítulos (listagem por idioma e grupo, páginas, download). Obras que não estão no
MangaDex só entram por upload.

O [manga-downloader](https://github.com/elboletaire/manga-downloader) baixa capítulos de ~555
sites. Pontos que pesaram, conferidos em 2026-11-02 (v24.1.0):

- É uma CLI em Go, **AGPL-3.0**, sem API. Grava CBZ, CBR, EPUB ou pasta de imagens, com nome por
  template (`Series`, `Number`, `Title`, `Group`, `Volume`).
- **Não lista capítulos sem baixar.** A listagem existe por dentro (`core.Fetch` devolve título e
  capítulos com `ID` estável, número, título, idioma, grupo, volume e bloqueio), mas a CLI não a
  expõe. O download é por faixa de números, com filtros de idioma e grupo.
- ~24 sites exigem Chromium com tela virtual para passar pelo Cloudflare (a imagem `:browser`
  tem ~408 MB compactados; a sem navegador, ~11 MB). Desafio que pede uma pessoa falha sem ela.
- Releases frequentes (três numa semana), porque os sites mudam.

O AniList (GraphQL, sem chave, 30 req/min no header em 2026-11-02) descreve mangá, manhwa e manhua
com capa, sinopse **só em inglês** com HTML, 19 gêneros e 430 tags com `rank` (0–100) e
`isMediaSpoiler`. **Não lista capítulos** (`chapters` é só o total, `null` em obra em andamento) e
**não cobre HQ ocidental, HQ brasileira nem livro**.

**Decisão:**

- **Papéis separados.** Metadados vêm do **AniList**; capítulos e páginas vêm do
  **manga-downloader**, para qualquer site que ele suporte, inclusive o mangadex.org. Escopo:
  mangá, manhwa e manhua. HQ e livro ficam para depois.
- **Só ADMIN**, em `/admin/sourcing`, como hoje. **Import pontual** agora; o sync é outra ADR,
  e o modelo já nasce para recebê-lo.
- **Fluxo do import:**
  1. busca no AniList e escolha da obra, ou escolha de uma obra que já está no catálogo (anexa a
     ela);
  2. URL da série no site;
  3. listagem: idiomas, grupos e capítulos, com os bloqueados (moedas, acesso antecipado)
     visíveis e desabilitados;
  4. escolha dos idiomas e do grupo preferido de cada um;
  5. revisão dos metadados. A sinopse em inglês vem num campo editável para o ADM traduzir.
     Versão sem idioma reconhecido pede o idioma ao ADM;
  6. confirmação.
- **Obra:** guarda o id do AniList. Import de um id que já tem obra anexa a ela em vez de criar
  outra. A capa é copiada para o bucket, como no MangaDex.
- **Tags:** tabela fixa AniList → vocabulário que já existe (o mesmo de `SourceTagTranslations`),
  para obra do AniList e obra do MangaDex caírem nas mesmas tags do filtro. Entra só tag com
  **rank ≥ 80**. Tag sem equivalente é ignorada, e o catálogo não cresce. Tag marcada como spoiler
  na obra fica de fora. `isAdult` e as tags de conteúdo sexual viram aviso de conteúdo. As técnicas
  (Full Color, Long Strip, 4-koma) entram em formato, e as de demografia em demografia.
- **Listagem por `list --json`**, contribuída no manga-downloader (sem fork). O Burūna recebe o
  JSON da listagem e grava uma `SourceChapter` por versão, com o `ID` do site como `externalId`.
  **Plano B**, se o PR for recusado ou demorar: um binário Go próprio, num repositório público
  separado com licença AGPL, que importa o pacote `core` e imprime o mesmo JSON. O Burūna o chama
  como processo, sem linkar.
- **Download por número:** um processo por capítulo, com `--language`, `--scanlator` e o número.
  O PR não inclui download por id. Se o mesmo grupo tiver duas versões do mesmo número no mesmo
  idioma, a CLI escolhe e o ADM confere.
- **Modelo:** `SourceName` ganha `MANGA_DOWNLOADER`, e `TrackedWork` ganha a URL da série (o
  domínio sai dela). **Uma obra pode ter várias fontes** (o MangaDex legado e um site, ou dois
  sites). A escolha de versão é a do `ChapterSelection`: grupo preferido, senão a mais recente.
  **Número que já existe na obra fica**, venha de upload, do MangaDex ou de outra fonte: a versão
  nova fica `IGNORED` sem ser baixada, porque a checagem consulta os capítulos da obra (use case
  público do `work`) antes do download, e não só as versões da mesma fonte. O ADM pode trocá-la
  pelo "usar esta versão", que hoje só acha o capítulo ocupante entre as versões da mesma
  `TrackedWork` e passa a procurá-lo na obra inteira.
- **Execução no Job** ([ADR-48](ADR-48-ingest-em-cloud-run-job.md)):
  - **imagem própria do Job**, com backend, manga-downloader, Chromium e tela virtual, saída de um
    segundo `--target` do mesmo Dockerfile. O serviço web continua sem navegador, e o deploy passa
    a publicar duas imagens. Deixa de valer o "mesma imagem do backend" da ADR-48;
  - pede **CBZ** à CLI e extrai com o `PageExtractor`, com os mesmos limites do upload (páginas,
    tamanho total, tamanho por página, tipo pelos magic bytes);
  - a **versão leve** de cada página é gerada no ingest, no perfil das `data-saver` que o MangaDex
    entrega hoje, medido nas que já estão no bucket. O leitor não muda;
  - **lease único** `MANGA_DOWNLOADER` para todos os sites: um executor por vez. Capítulo de
    mangadex.org segura **também** o lease `MANGADEX`, para não bater no MangaDex junto com o sync
    legado. Se o `MANGADEX` estiver ocupado, o capítulo fica `PENDING` para a próxima rodada e o
    executor segue com os outros sites, sem esperar. Uma rodada do sync legado pode levar horas;
    esperar travaria todos os sites por causa de um;
  - paralelismo da CLI por variável de ambiente (`--concurrency`, `--concurrency-pages`), começando
    conservador;
  - processo sem shell, com timeout e diretório próprio por capítulo. Falha vira `FAILED` com o
    fim da saída de erro como motivo e tem retry, como no MangaDex.
- **Versão do binário fixa**, com a imagem por tag no Dockerfile (`COPY --from=elboletaire/manga-downloader:<versão>`).
  O Dependabot abre PR a cada release, a CI valida e o merge é manual.
- **Crédito ao leitor:** só o grupo de tradução, sem o site de origem. Exceção para o mangadex.org:
  grupo e MangaDex com link, que a AUP exige.
- **Andamento:** tela de capítulos com estado, motivo e retry, como a de hoje, e o
  `SourcingDigest` diário.
- **MangaDex legado:** as obras já acompanhadas seguem no adaptador atual, com sync a cada 3 h
  ([ADR-49](ADR-49-sync-das-obras-acompanhadas.md)), até o sync pelo manga-downloader existir.
  Import novo, inclusive de mangadex.org, só pelo caminho novo.
- **`ExternalSource` se divide** em uma porta de metadados (AniList) e uma de capítulos
  (manga-downloader). O adaptador do MangaDex implementa as duas enquanto o legado existir.

**Riscos aceitos:**

- **SSRF:** a URL da série não é validada antes de ir para a CLI. A CLI aceita domínios que não
  conhece quando são de uma plataforma suportada, então uma URL interna ou do servidor de
  metadados do GCP seria buscada pelo Job. Aceito porque só o ADM importa.
- **Remoção de conteúdo:** "qualquer site" inclui agregadores de scans sem licença. Não há
  mecanismo novo: o ADM apaga obra ou capítulo, como hoje.
- **Cloudflare a partir do Cloud Run:** o Job sai por IP de datacenter, tratado com mais
  desconfiança. Os sites com navegador devem falhar mais que na máquina de alguém.
- **AGPL:** usar o binário sem modificar, como processo separado, não muda a licença do Burūna
  (Apache-2.0). Modificar o manga-downloader, ou o wrapper do plano B, obriga a publicar o
  código.

**Alternativas descartadas:**

- Manter o adaptador do MangaDex para o mangadex.org: dois caminhos para a mesma fonte.
- Lease por domínio: sites diferentes em paralelo, mas mais Jobs e mais estado. O volume de
  hoje cabe num executor.
- Chromium na imagem única: ~400 MB e os CVEs do navegador no serviço exposto à internet.
- Sem Chromium: deixaria de fora ~4% dos sites.
- Pasta de imagens (`--format raw`) em vez de CBZ: replicaria os limites do `PageExtractor`.
- Traduzir as 430 tags: o `TagSelector` ficaria seis vezes maior, com tags de cauda longa
  ("Ensemble Cast").
- MangaUpdates (busca difusa, tags votadas), MangaDex só para metadados (contradiz a troca) e
  Jikan (API não oficial) como fonte de metadados.
- Faixa digitada às cegas, gallery-dl só para listar (outros ids e outra cobertura), fork do
  manga-downloader e Suwayomi (serviço com estado) para a listagem.

**Tradeoff:** a listagem depende de um projeto de terceiros aceitar um PR, e o plano B é código
AGPL nosso para manter. O deploy ganha uma imagem pesada só para o Job. A sinopse e as tags passam
a vir de uma base em inglês, revisada à mão a cada import. Em troca, o Burūna passa a importar de
centenas de sites com um caminho só, e os metadados vêm de uma base que não depende do site de
onde vêm as páginas.

**Atualização (2026-11-02), implementação dos passos 3 e 4:**

- **A listagem roda no serviço web.** A ADR previa a CLI só no Job, mas a tela de import lista
  antes de confirmar, numa requisição. O serviço leva só o binário (~7 MB), sem navegador: site
  que exige navegador não lista ali, a tela mostra o motivo, e o import segue com idioma e grupo
  informados. O Job tem o binário e o Chromium.
- **A URL da série é o `external_id`** da obra acompanhada, que sobe para 500 caracteres (V34).
- **Executor genérico.** O `SourcingRunner`, a listagem e o importer recebem a fonte; só listar,
  dizer os idiomas disponíveis e baixar as páginas ficam atrás de `ChapterSource` (o MangaDex
  embrulha o código que já existia). Uma execução do Job por fonte (`--sourcing.source`).
- **Capítulo sem id no site:** a chave é `sem-id:` + número (ou título) + idioma + grupos.
- **Grupo:** o id é o nome codificado em URL (os ids são guardados juntos por vírgula, e um nome
  pode ter vírgula); o `list --json` ganhou o campo `groups` com os nomes separados.
- **Idioma do site:** cada idioma acompanhado guarda o código do site (`source_language`); vazio é
  o site que não informa idioma.
- **Uma listagem por idioma no Job** (2026-11-07, v2.2.1). Em alguns sites, `--language all` é o mesmo
  que nenhum filtro e traz só o idioma padrão deles: no mangafire.to, um import em pt-br listou só
  inglês e não trouxe nada. O Job lista cada idioma acompanhado com `--language <código>`, e o
  capítulo fica com o código pedido. A tela e o painel "Idiomas" mostram também os idiomas que o site
  oferece no filtro dele, mesmo os que a listagem padrão não trouxe.
- **Código do site visível e corrigível** (2026-11-09, #142). No modo manual, o código vazio passava
  sem aviso: o Jujutsu Kaisen do mangafire ficou sem capítulos, porque vazio lista o padrão do site
  (inglês) e guarda só os capítulos sem idioma. Agora a lista de acompanhadas mostra o código de cada
  idioma, e um idioma sem capítulos, com o site listando outros, vira aviso na obra
  (`tracked_works.listing_notice`, regravado a cada listagem que dá certo). O painel "Idiomas" aceita
  o código digitado à mão, e acompanhar de novo um idioma do Burūna troca o código dele. Código vazio
  digitado à mão só passa com a confirmação "Este site não informa idioma".
- **Proteção da CLI:** a URL precisa ser `http(s)` e vai depois de `--`, para não virar flag.
- **Colisão** continua como no MangaDex (`FAILED` com o motivo, sem baixar, porque o registro vem
  antes do download). O `IGNORED` e o "usar esta versão" na obra inteira ficaram para um PR próprio.

**Atualização (2026-11-03), import pelo AniList:** com `aniListId`, o import de site anexa à obra
que já tem esse id (`works.anilist_id`, V36, sem UNIQUE: a checagem fica sob lock no lease da
fonte) ou cria uma com os metadados do AniList e a sinopse revisada pelo ADM. A prévia
(`/metadata/works/{id}`) avisa quando o id já tem obra no catálogo. O pedido traz exatamente uma
obra: `linkWorkId` ou `aniListId`.

**Pendente:**

- ~~PR `list --json` no manga-downloader~~: aceito (elboletaire/manga-downloader#449, com o campo
  `groups` pedido na revisão) e publicado na v25.0.0. O plano B (wrapper AGPL) não foi preciso.
- Sync pelo manga-downloader (outra ADR). Até lá, capítulo novo de obra importada pelo caminho
  novo é um import manual.
- Conferir, na implementação, se o binário da imagem oficial roda na base Alpine do Job e como
  instalar o Chromium e a tela virtual nela.
- Metadados de HQ e livro, fora do AniList.
