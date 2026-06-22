package com.buruna.admin.controller;

import com.buruna.identity.application.admin.InactivityJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/jobs")
public class JobController {

    private final InactivityJob inactivityJob;
    private final String jobSecret;

    public JobController(InactivityJob inactivityJob,
                         @Value("${app.jobs.secret}") String jobSecret) {
        this.inactivityJob = inactivityJob;
        this.jobSecret = jobSecret;
    }

    @PostMapping("/inactivity")
    public ResponseEntity<String> triggerInactivity(
            @RequestHeader("X-Job-Secret") String secret) {
        if (!jobSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        inactivityJob.runJob();
        return ResponseEntity.ok("Job executado");
    }
}
