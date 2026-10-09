package com.buruna.reading.application;

import com.buruna.work.application.GetWorkInfoUseCase;
import com.buruna.work.application.GetVolumeAccessUseCase;
import com.buruna.work.application.GetVolumeIdsByWorkUseCase;
import com.buruna.work.application.GetVolumeInfoUseCase;
import com.buruna.work.application.WorkInfo;
import com.buruna.work.application.VolumeInfo;
import com.buruna.work.application.VolumeReadInfo;
import com.buruna.reading.domain.ReadingHistory;
import com.buruna.reading.domain.ReadingProgress;
import com.buruna.reading.persistence.ReadingHistoryRepository;
import com.buruna.reading.persistence.ReadingProgressRepository;



import com.buruna.shared.storage.StorageClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReadingService {

    private static final int SIGNED_URL_EXPIRATION_SECONDS = 1800;
    private static final Duration SIGNED_URL_DURATION = Duration.ofSeconds(SIGNED_URL_EXPIRATION_SECONDS);

    private final GetVolumeAccessUseCase volumeAccessUseCase;
    private final GetVolumeInfoUseCase volumeInfoUseCase;
    private final GetWorkInfoUseCase workInfoUseCase;
    private final GetVolumeIdsByWorkUseCase volumeIdsByWorkUseCase;
    private final ReadingProgressRepository progressRepository;
    private final ReadingHistoryRepository historyRepository;
    private final StorageClient storageClient;

    public ReadingService(GetVolumeAccessUseCase volumeAccessUseCase,
                          GetVolumeInfoUseCase volumeInfoUseCase,
                          GetWorkInfoUseCase workInfoUseCase,
                          GetVolumeIdsByWorkUseCase volumeIdsByWorkUseCase,
                          ReadingProgressRepository progressRepository,
                          ReadingHistoryRepository historyRepository,
                          StorageClient storageClient) {
        this.volumeAccessUseCase = volumeAccessUseCase;
        this.volumeInfoUseCase = volumeInfoUseCase;
        this.workInfoUseCase = workInfoUseCase;
        this.volumeIdsByWorkUseCase = volumeIdsByWorkUseCase;
        this.progressRepository = progressRepository;
        this.historyRepository = historyRepository;
        this.storageClient = storageClient;
    }

    @Transactional
    public VolumeUrlResponse getVolumeUrl(UUID volumeId, UUID actorId) {
        VolumeReadInfo access = volumeAccessUseCase.openVolume(volumeId, actorId);

        ReadingHistory entry = new ReadingHistory();
        entry.setUserId(actorId);
        entry.setVolumeId(volumeId);
        historyRepository.save(entry);

        String signedUrl = storageClient
                .generateSignedUrl(access.fileUrl(), SIGNED_URL_DURATION)
                .toString();

        return new VolumeUrlResponse(volumeId, signedUrl, SIGNED_URL_EXPIRATION_SECONDS);
    }

    @Transactional
    public ProgressResponse saveProgress(UUID volumeId, int currentPage, Integer totalPages, UUID actorId) {
        volumeAccessUseCase.validateAccess(volumeId, actorId);

        ReadingProgress progress = progressRepository
                .findByUserIdAndVolumeId(actorId, volumeId)
                .orElseGet(() -> ReadingProgress.start(actorId, volumeId));

        progress.recordPage(currentPage, totalPages);
        progressRepository.saveAndFlush(progress);

        return ProgressResponse.from(progress);
    }

    @Transactional(readOnly = true)
    public Optional<ProgressResponse> getProgress(UUID workId, UUID actorId) {
        workInfoUseCase.requireExists(workId);

        List<UUID> volumeIds = volumeIdsByWorkUseCase.getVolumeIdsOrderedByNumberDesc(workId);
        if (volumeIds.isEmpty()) {
            return Optional.empty();
        }

        // Picks the progress on the highest-numbered volume (volumeIds is ordered DESC)
        return progressRepository.findByUserIdAndVolumeIdIn(actorId, volumeIds).stream()
                .min(Comparator.comparingInt(p -> volumeIds.indexOf(p.getVolumeId())))
                .map(ProgressResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<HistoryResponse> getHistory(UUID actorId, Pageable pageable) {
        Page<ReadingHistory> historyPage =
                historyRepository.findByUserIdOrderByReadAtDesc(actorId, pageable);

        Set<UUID> volumeIds = historyPage.getContent().stream()
                .map(ReadingHistory::getVolumeId)
                .collect(Collectors.toSet());

        Map<UUID, VolumeInfo> volumeInfos = volumeInfoUseCase.getInfoByIds(volumeIds);

        Set<UUID> workIds = volumeInfos.values().stream()
                .map(VolumeInfo::workId)
                .collect(Collectors.toSet());

        Map<UUID, WorkInfo> workInfos = workInfoUseCase.getInfoByIds(workIds);

        return historyPage.map(h -> {
            VolumeInfo vol = volumeInfos.get(h.getVolumeId());
            if (vol == null) return null;
            WorkInfo work = workInfos.get(vol.workId());
            if (work == null) return null;
            String coverUrl = work.coverUrl() != null
                    ? storageClient.generateSignedUrl(work.coverUrl(), Duration.ofHours(1)).toString()
                    : null;
            return new HistoryResponse(h.getVolumeId(), vol.volumeNumber(), vol.workId(),
                    work.title(), coverUrl, h.getReadAt());
        });
    }

    @Transactional(readOnly = true)
    public Map<UUID, ProgressResponse> getBatchProgress(List<UUID> volumeIds, UUID actorId) {
        return progressRepository.findByUserIdAndVolumeIdIn(actorId, volumeIds)
                .stream()
                .collect(Collectors.toMap(ReadingProgress::getVolumeId, ProgressResponse::from));
    }

    @Transactional(readOnly = true)
    public Optional<ProgressResponse> findProgressByVolume(UUID volumeId, UUID actorId) {
        return progressRepository.findByUserIdAndVolumeId(actorId, volumeId)
                .map(ProgressResponse::from);
    }
}
