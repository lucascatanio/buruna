package com.buruna.work.web;

import com.buruna.identity.domain.Role;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Capítulos de obras do catálogo público: colaborador dono ou ADMIN (ADR-44). Sem quota. */
@RestController
@RequestMapping("/works/{workId}/chapters")
public class ChapterController {

    private final GenerateChapterUploadUrlUseCase generateUploadUrl;
    private final FinalizeChapterUploadUseCase finalizeUpload;
    private final RetryChapterIngestUseCase retryIngest;
    private final DeleteChapterUseCase deleteChapter;

    public ChapterController(GenerateChapterUploadUrlUseCase generateUploadUrl,
                                    FinalizeChapterUploadUseCase finalizeUpload,
                                    RetryChapterIngestUseCase retryIngest,
                                    DeleteChapterUseCase deleteChapter) {
        this.generateUploadUrl = generateUploadUrl;
        this.finalizeUpload = finalizeUpload;
        this.retryIngest = retryIngest;
        this.deleteChapter = deleteChapter;
    }

    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    @PostMapping("/upload-url")
    public ResponseEntity<ChapterUploadUrlResponse> getUploadUrl(@PathVariable UUID workId,
                                                                 @Valid @RequestBody ChapterUploadUrlRequest request,
                                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(generateUploadUrl.handle(workId, ChapterScope.PUBLIC, request, actor(user)));
    }

    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    @PostMapping("/finalize")
    public ResponseEntity<ChapterResponse> finalizeUpload(@PathVariable UUID workId,
                                                          @Valid @RequestBody ChapterFinalizeRequest request,
                                                          @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(finalizeUpload.handle(workId, ChapterScope.PUBLIC, request, actor(user)));
    }

    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    @PostMapping("/{chapterId}/retry")
    public ResponseEntity<ChapterResponse> retry(@PathVariable UUID workId, @PathVariable UUID chapterId,
                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(retryIngest.handle(workId, chapterId, ChapterScope.PUBLIC, actor(user)));
    }

    @PreAuthorize("hasAnyRole('COLLABORATOR', 'ADMIN')")
    @DeleteMapping("/{chapterId}")
    public ResponseEntity<Void> delete(@PathVariable UUID workId, @PathVariable UUID chapterId,
                                       @AuthenticationPrincipal User user) {
        deleteChapter.handle(workId, chapterId, ChapterScope.PUBLIC, actor(user));
        return ResponseEntity.noContent().build();
    }

    private static ChapterActor actor(User user) {
        return new ChapterActor(user.getId(), user.getRole() == Role.ADMIN, null);
    }
}
