package com.buruna.work.web;

import com.buruna.identity.domain.User;
import com.buruna.work.application.ChapterActor;
import com.buruna.work.application.ChapterFinalizeRequest;
import com.buruna.work.application.ChapterResponse;
import com.buruna.work.application.ChapterScope;
import com.buruna.work.application.ChapterUploadUrlRequest;
import com.buruna.work.application.ChapterUploadUrlResponse;
import com.buruna.work.application.DeleteChapterUseCase;
import com.buruna.work.application.FinalizeChapterUploadUseCase;
import com.buruna.work.application.GenerateChapterUploadUrlUseCase;
import com.buruna.work.application.RetryChapterIngestUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Capítulos da coleção privada do dono (ADR-44). A quota vale aqui. */
@RestController
@RequestMapping("/my/works/{workId}/chapters")
public class PrivateChapterController {

    private final GenerateChapterUploadUrlUseCase generateUploadUrl;
    private final FinalizeChapterUploadUseCase finalizeUpload;
    private final RetryChapterIngestUseCase retryIngest;
    private final DeleteChapterUseCase deleteChapter;

    public PrivateChapterController(GenerateChapterUploadUrlUseCase generateUploadUrl,
                                    FinalizeChapterUploadUseCase finalizeUpload,
                                    RetryChapterIngestUseCase retryIngest,
                                    DeleteChapterUseCase deleteChapter) {
        this.generateUploadUrl = generateUploadUrl;
        this.finalizeUpload = finalizeUpload;
        this.retryIngest = retryIngest;
        this.deleteChapter = deleteChapter;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<ChapterUploadUrlResponse> getUploadUrl(@PathVariable UUID workId,
                                                                 @Valid @RequestBody ChapterUploadUrlRequest request,
                                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(generateUploadUrl.handle(workId, ChapterScope.PRIVATE, request, actor(user)));
    }

    @PostMapping("/finalize")
    public ResponseEntity<ChapterResponse> finalizeUpload(@PathVariable UUID workId,
                                                          @Valid @RequestBody ChapterFinalizeRequest request,
                                                          @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(finalizeUpload.handle(workId, ChapterScope.PRIVATE, request, actor(user)));
    }

    @PostMapping("/{chapterId}/retry")
    public ResponseEntity<ChapterResponse> retry(@PathVariable UUID workId, @PathVariable UUID chapterId,
                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(retryIngest.handle(workId, chapterId, ChapterScope.PRIVATE, actor(user)));
    }

    @DeleteMapping("/{chapterId}")
    public ResponseEntity<Void> delete(@PathVariable UUID workId, @PathVariable UUID chapterId,
                                       @AuthenticationPrincipal User user) {
        deleteChapter.handle(workId, chapterId, ChapterScope.PRIVATE, actor(user));
        return ResponseEntity.noContent().build();
    }

    private static ChapterActor actor(User user) {
        return new ChapterActor(user.getId(), false, user.getQuotaGb());
    }
}
