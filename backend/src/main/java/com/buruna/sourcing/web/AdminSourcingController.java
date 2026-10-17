package com.buruna.sourcing.web;

import com.buruna.identity.domain.User;
import com.buruna.sourcing.application.ImportWorkRequest;
import com.buruna.sourcing.application.ImportWorkUseCase;
import com.buruna.sourcing.application.ListTrackedWorksUseCase;
import com.buruna.sourcing.application.RequestSourcingRunUseCase;
import com.buruna.sourcing.application.SearchSourceUseCase;
import com.buruna.sourcing.application.SourceGroupSummary;
import com.buruna.sourcing.application.SourceSearchResult;
import com.buruna.sourcing.application.TrackedWorkResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** Importação de obras do MangaDex pelo ADM (ADR-46). */
@RestController
@RequestMapping("/admin/sourcing")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminSourcingController {

    private final SearchSourceUseCase searchSource;
    private final ImportWorkUseCase importWork;
    private final ListTrackedWorksUseCase listTrackedWorks;
    private final RequestSourcingRunUseCase requestRun;

    public AdminSourcingController(SearchSourceUseCase searchSource,
                                   ImportWorkUseCase importWork,
                                   ListTrackedWorksUseCase listTrackedWorks,
                                   RequestSourcingRunUseCase requestRun) {
        this.searchSource = searchSource;
        this.importWork = importWork;
        this.listTrackedWorks = listTrackedWorks;
        this.requestRun = requestRun;
    }

    @GetMapping("/search")
    public List<SourceSearchResult> search(@RequestParam("q") @NotBlank @Size(max = 200) String query) {
        return searchSource.search(query.trim());
    }

    @GetMapping("/works/{externalId}/groups")
    public List<SourceGroupSummary> groups(@PathVariable @Size(max = 100) String externalId,
                                           @RequestParam @NotBlank @Size(max = 35) String language) {
        return searchSource.groups(externalId, language);
    }

    @GetMapping("/cover")
    public ResponseEntity<byte[]> cover(@RequestParam @Size(max = 500) String url) {
        return searchSource.coverThumbnail(url)
                .map(cover -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(cover.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                        .body(cover.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/imports")
    public ResponseEntity<TrackedWorkResponse> importWork(@Valid @RequestBody ImportWorkRequest request,
                                                          @AuthenticationPrincipal User admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importWork.handle(request, admin.getId()));
    }

    /** 202: a rodada roda no Job (no profile local, antes de responder). */
    @PostMapping("/run")
    public ResponseEntity<Void> run() {
        requestRun.handle();
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/tracked")
    public List<TrackedWorkResponse> tracked() {
        return listTrackedWorks.handle();
    }
}
