package com.buruna.admin.web;

import com.buruna.identity.application.admin.RunInactivityUseCase;
import com.buruna.manga.application.maintenance.DeleteOrphanVolumeFilesUseCase;
import com.buruna.manga.application.maintenance.OrphanVolumeFilesResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/admin/jobs")
public class JobController {

    private final RunInactivityUseCase runInactivityUseCase;
    private final DeleteOrphanVolumeFilesUseCase deleteOrphanVolumeFilesUseCase;
    private final byte[] jobSecret;

    public JobController(RunInactivityUseCase runInactivityUseCase,
                         DeleteOrphanVolumeFilesUseCase deleteOrphanVolumeFilesUseCase,
                         @Value("${app.jobs.secret}") String jobSecret) {
        this.runInactivityUseCase = runInactivityUseCase;
        this.deleteOrphanVolumeFilesUseCase = deleteOrphanVolumeFilesUseCase;
        this.jobSecret = jobSecret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/inactivity")
    public ResponseEntity<String> triggerInactivity(
            @RequestHeader("X-Job-Secret") String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        runInactivityUseCase.run();
        return ResponseEntity.ok("Job executado");
    }

    @PostMapping("/storage-orphans")
    public ResponseEntity<?> triggerStorageOrphans(
            @RequestHeader("X-Job-Secret") String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        OrphanVolumeFilesResult result = deleteOrphanVolumeFilesUseCase.run();
        return ResponseEntity.ok(result);
    }

    // Comparação em tempo constante (MessageDigest.isEqual) em vez de String.equals,
    // que sai mais cedo no primeiro byte diferente e pode vazar o segredo por timing.
    private boolean isAuthorized(String secret) {
        return MessageDigest.isEqual(jobSecret, secret.getBytes(StandardCharsets.UTF_8));
    }
}
