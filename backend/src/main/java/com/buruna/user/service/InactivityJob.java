package com.buruna.user.service;

import com.buruna.infra.notification.EmailService;
import com.buruna.infra.storage.StorageClient;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.user.domain.User;
import com.buruna.user.domain.UserStatus;
import com.buruna.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class InactivityJob {

    private static final Logger log = LoggerFactory.getLogger(InactivityJob.class);

    private static final int WARNING_THRESHOLD_DAYS = 75;
    private static final int DEACTIVATION_THRESHOLD_DAYS = 90;

    private final UserRepository userRepository;
    private final MangaRepository mangaRepository;
    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final EmailService emailService;

    public InactivityJob(UserRepository userRepository,
                         MangaRepository mangaRepository,
                         VolumeRepository volumeRepository,
                         StorageClient storageClient,
                         EmailService emailService) {
        this.userRepository = userRepository;
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.emailService = emailService;
    }

    // roda todo dia às 02:00
    @Scheduled(cron = "0 0 2 * * *")
    public void runJob() {
        log.info("InactivityJob started");

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime warningCutoff = now.minusDays(WARNING_THRESHOLD_DAYS);
        OffsetDateTime deactivationCutoff = now.minusDays(DEACTIVATION_THRESHOLD_DAYS);

        // INFO: carrega todos os usuários ACTIVE em memória.
        // Adequado para ≤100 usuários (escopo atual). Se a base crescer,
        // substituir por processamento paginado com Pageable.
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);

        int warned = 0;
        int deactivated = 0;

        for (User user : activeUsers) {
            OffsetDateTime lastAccess = user.getLastAccessAt();
            if (lastAccess == null) lastAccess = user.getCreatedAt();

            if (lastAccess.isBefore(deactivationCutoff)) {
                List<String> fileUrls = deactivateUser(user);
                deleteFromGcs(fileUrls);
                deactivated++;
            } else if (lastAccess.isBefore(warningCutoff)) {
                emailService.sendInactivityWarning(user.getEmail(), user.getUsername());
                warned++;
            }
        }

        log.info("InactivityJob finished: {} warned, {} deactivated", warned, deactivated);
    }

    @Transactional
    public List<String> deactivateUser(User user) {
        List<com.buruna.manga.domain.Manga> privateMangas =
                mangaRepository.findByOwnerIdAndIsPublicFalse(user.getId());

        List<String> fileUrls = new ArrayList<>();
        for (com.buruna.manga.domain.Manga manga : privateMangas) {
            manga.getVolumes().forEach(v -> {
                if (v.getFileUrl() != null) fileUrls.add(v.getFileUrl());
            });
            if (manga.getCoverUrl() != null) fileUrls.add(manga.getCoverUrl());
        }

        if (!privateMangas.isEmpty()) {
            mangaRepository.deleteAll(privateMangas);
            log.info("Deleted {} private mangas for inactive user {}",
                    privateMangas.size(), user.getUsername());
        }

        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("User {} deactivated due to inactivity", user.getUsername());

        return fileUrls;
    }

    private void deleteFromGcs(List<String> fileUrls) {
        for (String url : fileUrls) {
            storageClient.delete(url);
        }
    }
}