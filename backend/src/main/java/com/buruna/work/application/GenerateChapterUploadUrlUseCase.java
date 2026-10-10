package com.buruna.work.application;

import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.ChapterObjectName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/** Fase 1 do upload de capítulo: URL assinada de PUT para {@code pending/} (ADR-24, ADR-40). */
@Service
public class GenerateChapterUploadUrlUseCase {

    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(15);

    private final ChapterUploadAccess access;
    private final RegisterChapterUseCase registerChapter;
    private final StorageClient storageClient;

    public GenerateChapterUploadUrlUseCase(ChapterUploadAccess access, RegisterChapterUseCase registerChapter,
                                           StorageClient storageClient) {
        this.access = access;
        this.registerChapter = registerChapter;
        this.storageClient = storageClient;
    }

    // sem lock: é só um aviso antecipado; a regra vale de verdade no finalize
    @Transactional
    public ChapterUploadUrlResponse handle(UUID workId, ChapterScope scope, ChapterUploadUrlRequest request,
                                           ChapterActor actor) {
        access.findWork(workId, scope, actor);
        registerChapter.assertNumberFree(workId, request.language(), request.number());

        String objectName = ChapterObjectName.pendingFor(workId);
        var signedUpload = storageClient.generateUploadSignedUrl(objectName, UPLOAD_URL_EXPIRATION);
        return new ChapterUploadUrlResponse(signedUpload.url().toString(), objectName, signedUpload.requiredHeaders());
    }
}
