package com.buruna.admin.controller;

import com.buruna.admin.dto.SubmissionReviewRequest;
import com.buruna.manga.application.ListPendingSubmissionsUseCase;
import com.buruna.manga.application.ReviewSubmissionUseCase;
import com.buruna.manga.dto.PendingSubmissionResponse;
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

    private final ListPendingSubmissionsUseCase listPendingSubmissions;
    private final ReviewSubmissionUseCase reviewSubmission;

    public AdminSubmissionController(ListPendingSubmissionsUseCase listPendingSubmissions,
                                     ReviewSubmissionUseCase reviewSubmission) {
        this.listPendingSubmissions = listPendingSubmissions;
        this.reviewSubmission = reviewSubmission;
    }

    @GetMapping
    public ResponseEntity<Page<PendingSubmissionResponse>> listPending(
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(listPendingSubmissions.handle(pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id,
                                        @AuthenticationPrincipal User admin) {
        reviewSubmission.approve(id, admin.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                       @AuthenticationPrincipal User admin,
                                       @RequestBody(required = false) SubmissionReviewRequest request) {
        reviewSubmission.reject(id, admin.getId(), request != null ? request.rejectionReason() : null);
        return ResponseEntity.noContent().build();
    }
}
