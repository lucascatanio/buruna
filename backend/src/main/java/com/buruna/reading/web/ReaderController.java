package com.buruna.reading.web;

import com.buruna.reading.application.ReadingService;
import com.buruna.user.domain.User;
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
        return ResponseEntity.ok(readingService.saveProgress(volumeId, request.currentPage(), user.getId()));
    }

    @GetMapping("/progress/{mangaId}")
    public ResponseEntity<ProgressResponse> getProgress(
            @PathVariable UUID mangaId,
            @AuthenticationPrincipal User user) {
        return readingService.getProgress(mangaId, user.getId())
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
    public ResponseEntity<Map<UUID, Integer>> getBatchProgress(
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
}
