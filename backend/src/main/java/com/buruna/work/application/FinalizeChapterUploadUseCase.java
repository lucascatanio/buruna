package com.buruna.work.application;

import com.buruna.shared.exception.StorageObjectNotFoundException;
import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.ChapterKind;
import com.buruna.work.domain.ChapterObjectName;
import com.buruna.work.domain.PendingUploadNotFoundException;
import com.buruna.work.domain.Work;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Fase 2 do upload de capítulo: confere que o pendente é desta obra (ADR-40), cobra a quota no
 * escopo privado, registra o capítulo em processamento com o arquivo e pede a extração das
 * páginas depois do commit (ADR-48).
 */
@Service
public class FinalizeChapterUploadUseCase {

    private final ChapterUploadAccess access;
    private final RegisterChapterUseCase registerChapter;
    private final QuotaService quotaService;
    private final StorageClient storageClient;
    private final ChapterIngestTrigger ingestTrigger;

    public FinalizeChapterUploadUseCase(ChapterUploadAccess access,
                                        RegisterChapterUseCase registerChapter,
                                        QuotaService quotaService,
                                        StorageClient storageClient,
                                        ChapterIngestTrigger ingestTrigger) {
        this.access = access;
        this.registerChapter = registerChapter;
        this.quotaService = quotaService;
        this.storageClient = storageClient;
        this.ingestTrigger = ingestTrigger;
    }

    @Transactional
    public ChapterResponse handle(UUID workId, ChapterScope scope, ChapterFinalizeRequest request,
                                  ChapterActor actor) {
        Work work = access.findWork(workId, scope, actor);
        ChapterObjectName pending = ChapterObjectName.parsePending(request.objectName(), workId);
        work.assertAcceptsPagesFrom(pending.format());

        // objeto ausente = finalize repetido ou upload nunca feito: 404, não 500
        long sizeBytes;
        try {
            sizeBytes = storageClient.getFileMetadata(pending.pendingObjectName()).size();
        } catch (StorageObjectNotFoundException e) {
            throw new PendingUploadNotFoundException();
        }
        if (scope == ChapterScope.PRIVATE) {
            quotaService.assertCanFit(actor.actorId(), actor.quotaGb(), sizeBytes);
        }

        // registra antes de mover: se o número já existir, o arquivo continua em pending/ e
        // a lifecycle rule o limpa, em vez de virar órfão em chapter-sources/
        ChapterResponse chapter = registerChapter.handle(new RegisterChapterCommand(
                workId, request.language(), request.number(), request.label(), request.title(),
                request.scanlationGroup(), ChapterKind.PAGES, actor.actorId(),
                pending.sourceObjectName(), sizeBytes));
        try {
            storageClient.move(pending.pendingObjectName(), pending.sourceObjectName());
        } catch (StorageObjectNotFoundException e) {
            throw new PendingUploadNotFoundException();
        }

        ingestTrigger.requestIngest(chapter.id());
        return chapter;
    }
}
