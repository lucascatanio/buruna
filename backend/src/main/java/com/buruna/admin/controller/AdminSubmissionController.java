package com.buruna.admin.controller;

import com.buruna.admin.dto.SubmissionReviewRequest;
import com.buruna.manga.dto.PendingSubmissionResponse;
import com.buruna.manga.service.PrivateMangaService;
import com.buruna.identity.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/submissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubmissionController {

    private final PrivateMangaService privateMangaService;

    public AdminSubmissionController(PrivateMangaService privateMangaService) {
        this.privateMangaService = privateMangaService;
    }

    @GetMapping
    public ResponseEntity<Page<PendingSubmissionResponse>> listPending(
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(privateMangaService.listPendingSubmissions(pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id,
                                        @AuthenticationPrincipal User admin) {
        privateMangaService.approveSubmission(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                       @AuthenticationPrincipal User admin,
                                       @RequestBody(required = false) SubmissionReviewRequest request) {
        privateMangaService.rejectSubmission(id, admin, request != null ? request.rejectionReason() : null);
        return ResponseEntity.noContent().build();
    }
}
