package com.buruna.sourcing.web;

import com.buruna.identity.domain.User;
import com.buruna.sourcing.application.ImportWorkRequest;
import com.buruna.sourcing.application.ImportWorkUseCase;
import com.buruna.sourcing.application.LanguageAvailability;
import com.buruna.sourcing.application.ListTrackedWorksUseCase;
import com.buruna.sourcing.application.ManageTrackedWorkUseCase;
import com.buruna.sourcing.application.RequestSourcingRunUseCase;
import com.buruna.sourcing.application.SearchSourceUseCase;
import com.buruna.sourcing.application.SourceAlert;
import com.buruna.sourcing.application.SourceSearchResult;
import com.buruna.sourcing.application.TrackedGroupResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

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
    private final ManageTrackedWorkUseCase manage;

    public AdminSourcingController(SearchSourceUseCase searchSource,
                                   ImportWorkUseCase importWork,
                                   ListTrackedWorksUseCase listTrackedWorks,
                                   RequestSourcingRunUseCase requestRun,
                                   ManageTrackedWorkUseCase manage) {
        this.searchSource = searchSource;
        this.importWork = importWork;
        this.listTrackedWorks = listTrackedWorks;
        this.requestRun = requestRun;
        this.manage = manage;
    }

    @GetMapping("/search")
    public List<SourceSearchResult> search(@RequestParam("q") @NotBlank @Size(max = 200) String query) {
        return searchSource.search(query.trim());
    }

    @GetMapping("/works/{externalId}/availability")
    public LanguageAvailability availability(@PathVariable @Size(max = 100) String externalId,
                                             @RequestParam @NotBlank @Size(max = 35) String language) {
        return searchSource.availability(externalId, language);
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

    @PostMapping("/tracked/{id}/check")
    public ResponseEntity<Void> check(@PathVariable UUID id) {
        manage.check(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/tracked/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable UUID id) {
        manage.pause(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tracked/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable UUID id) {
        manage.resume(id);
        return ResponseEntity.noContent().build();
    }

    /** Idiomas que o MangaDex tem desta obra e que ainda não são acompanhados. */
    @GetMapping("/tracked/{id}/source-languages")
    public List<String> sourceLanguages(@PathVariable UUID id) {
        return manage.untrackedSourceLanguages(id);
    }

    /** Acrescenta um idioma à obra acompanhada (ou troca o grupo preferido dele). */
    @PostMapping("/tracked/{id}/languages")
    public ResponseEntity<Void> trackLanguage(@PathVariable UUID id,
                                              @Valid @RequestBody ImportWorkRequest.LanguageChoice request) {
        manage.trackLanguage(id, request.language(), request.preferredGroupId(), request.preferredGroupName());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/tracked/{id}/alerts")
    public List<SourceAlert> alerts(@PathVariable UUID id) {
        return manage.alerts(id);
    }

    @GetMapping("/tracked/{id}/groups")
    public List<TrackedGroupResponse> trackedGroups(@PathVariable UUID id) {
        return manage.groups(id);
    }

    /** Pedido de remoção de um grupo: apaga os capítulos dele e traz a versão de outro grupo. */
    @PostMapping("/tracked/{id}/ignored-groups")
    public ResponseEntity<Void> ignoreGroup(@PathVariable UUID id, @Valid @RequestBody IgnoreGroupRequest request,
                                            @AuthenticationPrincipal User admin) {
        manage.ignoreGroup(id, request.groupId(), request.groupName(), admin.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tracked/{id}/ignored-groups/{groupId}")
    public ResponseEntity<Void> stopIgnoringGroup(@PathVariable UUID id, @PathVariable @Size(max = 100) String groupId) {
        manage.stopIgnoringGroup(id, groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/source-chapters/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID id) {
        manage.retry(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/source-chapters/{id}/redownload")
    public ResponseEntity<Void> redownload(@PathVariable UUID id) {
        manage.redownload(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/source-chapters/{id}/use")
    public ResponseEntity<Void> useVersion(@PathVariable UUID id) {
        manage.useVersion(id);
        return ResponseEntity.accepted().build();
    }

    public record IgnoreGroupRequest(@NotBlank @Size(max = 100) String groupId, @Size(max = 255) String groupName) {
    }
}
