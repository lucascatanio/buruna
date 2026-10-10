package com.buruna.reading.web;

import com.buruna.identity.domain.User;
import com.buruna.reading.application.ChapterManifestResponse;
import com.buruna.reading.application.HistoryResponse;
import com.buruna.reading.application.ProgressResponse;
import com.buruna.reading.application.ReadingService;
import com.buruna.reading.application.VolumeUrlResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reader")
public class ReaderController {

    private final ReadingService readingService;

    public ReaderController(ReadingService readingService) {
        this.readingService = readingService;
    }

    @GetMapping("/{volumeId}/url")
    public ResponseEntity<VolumeUrlResponse> getVolumeUrl(
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingService.getVolumeUrl(volumeId, user.getId()));
    }

    @PostMapping("/{volumeId}/progress")
    public ResponseEntity<ProgressResponse> saveProgress(
            @PathVariable UUID volumeId,
            @Valid @RequestBody ProgressRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingService.saveProgress(volumeId, request.currentPage(), request.totalPages(), user.getId()));
    }

    @GetMapping("/progress/{workId}")
    public ResponseEntity<ProgressResponse> getProgress(
            @PathVariable UUID workId,
            @AuthenticationPrincipal User user) {
        return readingService.getProgress(workId, user.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    public ResponseEntity<Page<HistoryResponse>> getHistory(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(readingService.getHistory(user.getId(), pageable));
    }

    @GetMapping("/progress/batch")
    public ResponseEntity<Map<UUID, ProgressResponse>> getBatchProgress(
            @RequestParam List<UUID> volumeIds,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingService.getBatchProgress(volumeIds, user.getId()));
    }

    @GetMapping("/{volumeId}/progress")
    public ResponseEntity<ProgressResponse> getVolumeProgress(
            @PathVariable UUID volumeId,
            @AuthenticationPrincipal User user) {
        return readingService.findProgressByVolume(volumeId, user.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // ── Capítulos (ADR-44) ───────────────────────────────────────────────────

    @GetMapping("/chapters/{chapterId}")
    public ResponseEntity<ChapterManifestResponse> openChapter(
            @PathVariable UUID chapterId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingService.openChapter(chapterId, user.getId()));
    }

    @PostMapping("/chapters/{chapterId}/progress")
    public ResponseEntity<ProgressResponse> saveChapterProgress(
            @PathVariable UUID chapterId,
            @Valid @RequestBody ChapterProgressRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingService.saveChapterProgress(chapterId, request.currentPage(), user.getId()));
    }

    @GetMapping("/chapters/{chapterId}/progress")
    public ResponseEntity<ProgressResponse> getChapterProgress(
            @PathVariable UUID chapterId,
            @AuthenticationPrincipal User user) {
        return readingService.findChapterProgress(chapterId, user.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/works/{workId}/chapter-progress")
    public ResponseEntity<List<ProgressResponse>> getWorkChapterProgress(
            @PathVariable UUID workId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingService.getWorkChapterProgress(workId, user.getId()));
    }
}
