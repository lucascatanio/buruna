package com.buruna.admin.controller;

import com.buruna.identity.application.admin.RunInactivityUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/jobs")
public class JobController {

    private final RunInactivityUseCase runInactivityUseCase;
    private final String jobSecret;

    public JobController(RunInactivityUseCase runInactivityUseCase,
                         @Value("${app.jobs.secret}") String jobSecret) {
        this.runInactivityUseCase = runInactivityUseCase;
        this.jobSecret = jobSecret;
    }

    @PostMapping("/inactivity")
    public ResponseEntity<String> triggerInactivity(
            @RequestHeader("X-Job-Secret") String secret) {
        if (!jobSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        runInactivityUseCase.run();
        return ResponseEntity.ok("Job executado");
    }
}
