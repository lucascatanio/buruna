package com.buruna.manga.application.maintenance;

import com.buruna.manga.persistence.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import com.buruna.shared.storage.StorageClient.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Apaga de {@code volumes/} os arquivos que não têm linha na tabela {@code volumes}.
 * O delete de volume remove a linha na transação e o arquivo do storage depois,
 * best-effort (ADR-24); se o storage falha, o arquivo fica órfão. Disparado pelo
 * Cloud Scheduler via {@code POST /admin/jobs/storage-orphans} ({@code JobController}).
 *
 * <p>Carência de {@value #GRACE_DAYS} dias: só apaga órfão criado antes disso, para nunca
 * pegar um arquivo recém-movido para {@code volumes/} cuja linha ainda não foi gravada.
 * Tempo vem do {@link Clock} injetado (ADR-36).
 */
@Service
public class DeleteOrphanVolumeFilesUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteOrphanVolumeFilesUseCase.class);

    static final String PREFIX = "volumes/";
    static final int GRACE_DAYS = 7;
    private static final int LOOKUP_BATCH_SIZE = 500;

    private final StorageClient storageClient;
    private final VolumeRepository volumeRepository;
    private final Clock clock;

    public DeleteOrphanVolumeFilesUseCase(StorageClient storageClient,
                                          VolumeRepository volumeRepository,
                                          Clock clock) {
        this.storageClient = storageClient;
        this.volumeRepository = volumeRepository;
        this.clock = clock;
    }

    public OrphanVolumeFilesResult run() {
        log.info("DeleteOrphanVolumeFilesUseCase started");

        Instant cutoff = clock.instant().minus(Duration.ofDays(GRACE_DAYS));
        List<StoredObject> objects = storageClient.list(PREFIX);
        Set<String> referenced = findReferenced(objects);

        int orphans = 0;
        int deleted = 0;
        for (StoredObject object : objects) {
            if (referenced.contains(object.name()) || !object.createdAt().isBefore(cutoff)) {
                continue;
            }
            orphans++;
            if (delete(object.name())) {
                deleted++;
            }
        }

        log.info("DeleteOrphanVolumeFilesUseCase finished: {} analyzed, {} orphan(s), {} deleted",
                objects.size(), orphans, deleted);
        return new OrphanVolumeFilesResult(objects.size(), orphans, deleted);
    }

    private Set<String> findReferenced(List<StoredObject> objects) {
        Set<String> referenced = new HashSet<>();
        for (int from = 0; from < objects.size(); from += LOOKUP_BATCH_SIZE) {
            List<String> batch = objects.subList(from, Math.min(from + LOOKUP_BATCH_SIZE, objects.size()))
                    .stream().map(StoredObject::name).toList();
            referenced.addAll(volumeRepository.findExistingFileUrls(batch));
        }
        return referenced;
    }

    private boolean delete(String objectName) {
        try {
            storageClient.delete(objectName);
            return true;
        } catch (RuntimeException e) {
            log.warn("Failed to delete orphan GCS object {} (best-effort): {}", objectName, e.getMessage());
            return false;
        }
    }
}
