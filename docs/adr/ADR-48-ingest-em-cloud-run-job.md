# ADR-48 — Ingest de capítulos num Cloud Run Job

**Status:** Aceita

**Contexto:** Com o capítulo como unidade de leitura ([ADR-44](ADR-44-capitulo-como-unidade-de-leitura.md)),
o upload passa a aceitar CBZ (e, depois, CBR e PDF), e as páginas precisam ser extraídas no
servidor antes da leitura. A importação de fontes externas faz o mesmo trabalho: baixar
páginas e gravá-las no bucket. As opções eram processar no próprio serviço backend, dentro de
uma requisição (Pub/Sub push ou Cloud Tasks), num Cloud Run Job separado, ou no navegador.

O que pesou:

- No backend, a extração disputaria memória e CPU com os leitores na mesma instância, e um
  arquivo grande pede mais memória do que o serviço precisa no resto do tempo.
- Fora de uma requisição, o `cpu-throttling` do Cloud Run congela o trabalho
  ([ADR-03](ADR-03-async-e-scheduled-internos.md), [ADR-42](ADR-42-forgot-password-via-pubsub.md)).
  Pub/Sub push limita a 10 min e Cloud Tasks a 30 min por tarefa.
- No navegador, o celular do usuário faria o trabalho pesado, e o código de conversão
  existiria duas vezes (front e back).

**Decisão:**

- **Cloud Run Job `buruna-ingest`** em us-east1, com a **mesma imagem do backend** e o
  profile `ingest`. O `IngestJobRunner` recebe `--ingest.chapter-id=<id>`, chama o
  `ProcessChapterSourceUseCase` e encerra. Tem memória própria (2 GiB), CPU alocada do começo
  ao fim e tarefa de até 24 h.
- **Disparo:** o finalize do upload registra o capítulo em `PROCESSING` e, **depois do
  commit**, o `CloudRunChapterIngestTrigger` chama a API `jobs.run` com o id
  (`CloudRunJobLauncher`, REST com ADC, na própria requisição). Se o disparo falhar, o
  capítulo vai para `FAILED` com o arquivo mantido, e o usuário pode tentar de novo. No
  profile `local` o processamento roda na própria requisição (`InlineChapterIngestTrigger`).
- **Formato:** os bytes originais das imagens são mantidos (JPEG, PNG, WebP). GIF, BMP e TIFF
  viram JPEG com qualidade 0,85. A conversão e a extração moram em `shared/media`, porque o
  upload (contexto `work`) e a importação (contexto `sourcing`) usam as duas.
- **Segurança do arquivo:** até 1.000 páginas, 1 GB descompactado e 50 MB por página,
  aplicados sobre os bytes realmente lidos e não sobre o tamanho declarado no zip
  (`APP_INGEST_MAX_*`). Tipo de imagem pelos magic bytes, não pela extensão.
- **Arquivo enviado:** sai de `pending/` para `chapter-sources/` no finalize, fora do alcance
  da lifecycle rule de 1 dia, e é **apagado depois da extração**: as páginas já são os bytes
  originais. Falha transitória (storage, Job que caiu) mantém o arquivo e permite retry
  (`POST .../chapters/{id}/retry`). Arquivo inválido o apaga, com o motivo no capítulo,
  porque tentar de novo não resolveria.
- **Idempotência:** capítulo fora de `PROCESSING` é ignorado, e as páginas têm nome
  determinístico (`chapters/{chapterId}/{posição}.{ext}`). Uma reexecução sobrescreve os
  mesmos objetos. O processamento não segura transação longa: só lê o capítulo no começo e
  publica no fim, cada um numa transação própria (`REQUIRES_NEW`).
- **Quota:** passa a contar as páginas e os arquivos enviados ainda guardados.

**Tradeoff:** é uma peça de infra a mais (o Job, a permissão do backend para dispará-lo e a
atualização da imagem do Job no deploy), e o upload ganha a partida a frio da JVM (~10–30 s)
antes do processamento. O Job em us-east1 grava no bucket de São Paulo pagando transferência
entre regiões ([ADR-26](ADR-26-gcs-southamerica-cloudrun-useast1.md)), alguns dólares por
centena de obras importadas.

**Pendente:** o job de órfãos (`DeleteOrphanVolumeFilesUseCase`) só olha `volumes/`. Páginas
e arquivos de capítulo que sobrarem de um delete com falha no storage não são limpos ainda.
