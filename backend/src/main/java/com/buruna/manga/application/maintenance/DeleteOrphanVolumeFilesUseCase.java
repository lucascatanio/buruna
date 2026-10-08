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
 *
 * <p>Duas travas porque o job apaga arquivos de produção sem supervisão: {@code dryRun} só
 * conta (a primeira execução em produção deve ser assim), e, se os órfãos passarem de
 * {@value #MAX_ORPHANS_WITHOUT_RATIO_CHECK} e de {@value #MAX_ORPHAN_PERCENT}% do analisado,
 * nada é apagado: proporção assim indica descasamento entre o nome no bucket e o
 * {@code file_url} do banco, não falhas pontuais de deleção.
 */
@Service
public class DeleteOrphanVolumeFilesUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteOrphanVolumeFilesUseCase.class);

    static final String PREFIX = "volumes/";
    static final int GRACE_DAYS = 7;
    static final int MAX_ORPHANS_WITHOUT_RATIO_CHECK = 5;
    static final int MAX_ORPHAN_PERCENT = 10;
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

    public OrphanVolumeFilesResult run(boolean dryRun) {
        log.info("DeleteOrphanVolumeFilesUseCase started (dryRun={})", dryRun);

        Instant cutoff = clock.instant().minus(Duration.ofDays(GRACE_DAYS));
        List<StoredObject> objects = storageClient.list(PREFIX);
        Set<String> referenced = findReferenced(objects);
        List<String> orphans = objects.stream()
                .filter(object -> !referenced.contains(object.name()) && object.createdAt().isBefore(cutoff))
                .map(StoredObject::name)
                .toList();

        if (dryRun) {
            log.info("DeleteOrphanVolumeFilesUseCase dry run: {} analyzed, {} orphan(s), nothing deleted",
                    objects.size(), orphans.size());
            return new OrphanVolumeFilesResult(objects.size(), orphans.size(), 0, true, false);
        }
        if (tooManyOrphans(orphans.size(), objects.size())) {
            log.error("DeleteOrphanVolumeFilesUseCase aborted: {} orphan(s) of {} analyzed is above the "
                    + "safety ratio; nothing deleted. Check bucket names against volumes.file_url",
                    orphans.size(), objects.size());
            return new OrphanVolumeFilesResult(objects.size(), orphans.size(), 0, false, true);
        }

        int deleted = (int) orphans.stream().filter(this::delete).count();
        log.info("DeleteOrphanVolumeFilesUseCase finished: {} analyzed, {} orphan(s), {} deleted",
                objects.size(), orphans.size(), deleted);
        return new OrphanVolumeFilesResult(objects.size(), orphans.size(), deleted, false, false);
    }

    private static boolean tooManyOrphans(int orphans, int analyzed) {
        return orphans > MAX_ORPHANS_WITHOUT_RATIO_CHECK && orphans * 100L > (long) analyzed * MAX_ORPHAN_PERCENT;
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
