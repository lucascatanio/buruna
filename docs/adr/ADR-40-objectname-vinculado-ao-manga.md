# ADR-40 — objectName do volume vinculado ao mangá + prefixo pending/

**Status:** Aceita (correção da revisão de segurança de 2026-09-23)

**Contexto:** O upload de volume em duas fases (ADR-24) gerava, na fase 1, um
`objectName` sem nenhuma relação com o mangá — `volumes/{uuid}.pdf`, com um UUID
aleatório solto. A fase 2 (`FinalizeVolumeUseCase`/`FinalizePublicVolumeUseCase`)
aceitava esse `objectName` **do jeito que veio no corpo da requisição**, sem validar
que ele correspondia a um upload feito para aquele mangá.

Isso abria um vetor de ataque explorável por qualquer usuário cadastrado:

1. Abrir um volume público via `GET /reader/{id}/url` expõe, na URL assinada, o
   `objectName` real do arquivo (`volumes/<uuid>.pdf`).
2. Criar um mangá privado em `POST /my/mangas` (qualquer papel autenticado pode).
3. Chamar `POST /my/mangas/{id}/volumes/finalize` com o `objectName` do passo 1 —
   o finalize aceitava, sem checar posse nem origem do objeto.
4. Apagar esse volume forjado (`DELETE /my/mangas/{id}/volumes/{volumeId}`) chamava
   `storageClient.delete` no `objectName` compartilhado — **apagando o arquivo do
   volume público real**, que nada tinha a ver com o mangá privado do atacante.

O mesmo valia no sentido público→público via `FinalizePublicVolumeUseCase`. Um
achado relacionado, de severidade média, é que a Signed URL de upload não
limitava tamanho nem tinha prazo de limpeza garantido para uploads nunca finalizados
— o risco de órfão no bucket já registrado no ADR-24 sem uma mitigação implementada.

**Decisão:**

1. **Value Object `manga.domain.VolumeObjectName`** passa a ser a única forma de
   gerar e validar `objectName` de volume:
   - `pendingFor(UUID mangaId)` → `pending/volumes/{mangaId}/{uuid}.pdf` (fase 1).
   - `parsePending(String raw, UUID mangaId)` → valida com regex ancorada que `raw`
     é exatamente um pendente bem formado E que o `mangaId` do caminho é o do
     request atual; qualquer divergência (mangá errado, formato já finalizado,
     path traversal, segmento extra, UUID inválido) lança
     `InvalidVolumeObjectNameException` (`DomainErrorType.VALIDATION` → 400).
   - `finalObjectName()` → `volumes/{mangaId}/{uuid}.pdf` (fase 2, definitivo).
2. `Generate*VolumeUploadUrlUseCase` usam `pendingFor`. `Finalize*VolumeUseCase`
   fazem `parsePending` → `getFileMetadata` → quota/dedup (inalterados) →
   `storageClient.move(pending, final)` → `manga.addVolume(final, ...)`.
3. **Defesa para dados legados:** antes da correção, nada impedia que dois volumes
   (de mangás diferentes) apontassem para o mesmo `file_url` — quem explorou essa
   falha antes do deploy pode ter deixado dados assim. `manga.application.VolumeFileCleaner`
   centraliza a regra "só apaga o arquivo se nenhum OUTRO volume ainda o
   referencia" e é usado por todo ponto que apaga arquivo de volume
   (`DeleteVolumeUseCase`, `DeletePublicVolumeUseCase`, `DeleteMangaUseCase`,
   `DeletePrivateMangaUseCase`, `DeletePrivateCollectionForUserUseCase`). Capa e
   avatar não usam `objectName` vindo do cliente e ficam fora dessa regra.
4. **Limite de tamanho e limpeza de órfãos:**
   `StorageClient.generateUploadSignedUrl` passa a devolver
   `SignedUpload(URL url, Map<String,String> requiredHeaders)`. A implementação GCS
   assina a extension header `x-goog-content-length-range: 0,{maxBytes}`
   (`maxBytes` de `app.upload.max-file-size-mb`) via
   `Storage.SignUrlOption.withExtHeaders`; o GCS recusa o PUT se o `Content-Length`
   do corpo estiver fora do intervalo assinado. `LocalStorageClient` devolve um
   mapa vazio (não há GCS para impor o header). Novo `StorageClient.move(from, to)`
   (GCS: `copy` + `delete`; local: `Files.move`). O frontend envia esses headers no
   PUT através de um helper único (`frontend/src/api/volumeUpload.ts`).
   O prefixo `pending/` (item 1) somado a uma lifecycle rule do bucket que apaga
   esse prefixo após 1 dia (passo manual, fora do código) resolve o órfão descrito
   no ADR-24: um upload nunca finalizado nunca chega a `volumes/`.

**Por quê:** vincular o `objectName` ao `mangaId` do request fecha o vetor de raiz —
o finalize não confia mais em uma string arbitrária vinda do cliente, e sim em um
formato que só o próprio backend pôde ter emitido para aquele mangá específico. O
prefixo `pending/` separa objetos "em trânsito" (nunca referenciados por nenhum
`Volume` no banco) dos objetos definitivos, o que também é o que permite a lifecycle
rule de limpeza automática sem risco de apagar um arquivo em uso.

**Tradeoff:** `VolumeObjectName` amarra o esquema de nomes do bucket ao domínio —
qualquer mudança de convenção de path exige atualizar o VO e, potencialmente, dados
já existentes no bucket (a migração dos objetos já finalizados antes desta ADR não
foi feita: eles continuam em `volumes/<uuid>.pdf`, sem o `mangaId` no caminho, e
`VolumeObjectName` nunca precisa reanalisá-los — só o `objectName` do finalize passa
pela validação). O `VolumeFileCleaner` adiciona uma query por delete; irrelevante
para o volume de escrita deste domínio.
