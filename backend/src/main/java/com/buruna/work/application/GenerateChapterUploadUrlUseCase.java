package com.buruna.work.application;

import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.ChapterKind;
import com.buruna.work.domain.ChapterObjectName;
import com.buruna.work.domain.ChapterSourceFormat;
import com.buruna.work.domain.UnsupportedChapterSourceException;
import com.buruna.work.domain.Work;
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
        Work work = access.findWork(workId, scope, actor);
        ChapterSourceFormat format = request.format() == null || request.format().isBlank()
                ? ChapterSourceFormat.CBZ
                : ChapterSourceFormat.fromExtension(request.format())
                        .orElseThrow(() -> new UnsupportedChapterSourceException(
                                "Formato de arquivo não aceito: " + request.format() + ". Envie CBZ, CBR, PDF ou EPUB."));
        // edição de livro não tem número; só capítulo de imagens tem número a conferir
        if (work.chapterKindFor(format) == ChapterKind.PAGES) {
            registerChapter.assertNumberFree(workId, request.language(), request.number());
        }

        String objectName = ChapterObjectName.pendingFor(workId, format);
        var signedUpload = storageClient.generateUploadSignedUrl(objectName, format.contentType(), UPLOAD_URL_EXPIRATION);
        return new ChapterUploadUrlResponse(signedUpload.url().toString(), objectName, signedUpload.requiredHeaders());
    }
}
