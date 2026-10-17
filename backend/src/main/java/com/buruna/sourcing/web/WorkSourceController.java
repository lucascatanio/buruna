package com.buruna.sourcing.web;

import com.buruna.sourcing.application.ListTrackedWorksUseCase;
import com.buruna.sourcing.application.WorkSourceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Crédito público da fonte na página da obra (exigência da AUP do MangaDex, ADR-46). */
@RestController
public class WorkSourceController {

    private final ListTrackedWorksUseCase listTrackedWorks;

    public WorkSourceController(ListTrackedWorksUseCase listTrackedWorks) {
        this.listTrackedWorks = listTrackedWorks;
    }

    /** 204 quando a obra não veio de uma fonte externa. */
    @GetMapping("/works/{workId}/source")
    public ResponseEntity<WorkSourceResponse> source(@PathVariable UUID workId) {
        return listTrackedWorks.sourceOf(workId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
