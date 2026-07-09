package com.buruna.reading.application;

import com.buruna.manga.application.GetMangaInfoUseCase;
import com.buruna.manga.application.GetVolumeAccessUseCase;
import com.buruna.manga.application.GetVolumeIdsByMangaUseCase;
import com.buruna.manga.application.GetVolumeInfoUseCase;
import com.buruna.manga.application.MangaInfo;
import com.buruna.manga.application.VolumeInfo;
import com.buruna.manga.application.VolumeReadInfo;
import com.buruna.reading.domain.ReadingHistory;
import com.buruna.reading.domain.ReadingProgress;
import com.buruna.reading.persistence.ReadingHistoryRepository;
import com.buruna.reading.persistence.ReadingProgressRepository;
import com.buruna.reading.web.HistoryResponse;
import com.buruna.reading.web.ProgressResponse;
import com.buruna.reading.web.VolumeUrlResponse;
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
    private final GetMangaInfoUseCase mangaInfoUseCase;
    private final GetVolumeIdsByMangaUseCase volumeIdsByMangaUseCase;
    private final ReadingProgressRepository progressRepository;
    private final ReadingHistoryRepository historyRepository;
    private final StorageClient storageClient;

    public ReadingService(GetVolumeAccessUseCase volumeAccessUseCase,
                          GetVolumeInfoUseCase volumeInfoUseCase,
                          GetMangaInfoUseCase mangaInfoUseCase,
                          GetVolumeIdsByMangaUseCase volumeIdsByMangaUseCase,
                          ReadingProgressRepository progressRepository,
                          ReadingHistoryRepository historyRepository,
                          StorageClient storageClient) {
        this.volumeAccessUseCase = volumeAccessUseCase;
        this.volumeInfoUseCase = volumeInfoUseCase;
        this.mangaInfoUseCase = mangaInfoUseCase;
        this.volumeIdsByMangaUseCase = volumeIdsByMangaUseCase;
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
    public ProgressResponse saveProgress(UUID volumeId, int currentPage, UUID actorId) {
        volumeAccessUseCase.validateAccess(volumeId, actorId);

        ReadingProgress progress = progressRepository
                .findByUserIdAndVolumeId(actorId, volumeId)
                .orElseGet(() -> {
                    ReadingProgress p = new ReadingProgress();
                    p.setUserId(actorId);
                    p.setVolumeId(volumeId);
                    return p;
                });

        progress.setCurrentPage(currentPage);
        progressRepository.save(progress);

        return new ProgressResponse(volumeId, progress.getCurrentPage(), progress.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Optional<ProgressResponse> getProgress(UUID mangaId, UUID actorId) {
        mangaInfoUseCase.requireExists(mangaId);

        List<UUID> volumeIds = volumeIdsByMangaUseCase.getVolumeIdsOrderedByNumberDesc(mangaId);
        if (volumeIds.isEmpty()) {
            return Optional.empty();
        }

        // Picks the progress on the highest-numbered volume (volumeIds is ordered DESC)
        return progressRepository.findByUserIdAndVolumeIdIn(actorId, volumeIds).stream()
                .min(Comparator.comparingInt(p -> volumeIds.indexOf(p.getVolumeId())))
                .map(p -> new ProgressResponse(p.getVolumeId(), p.getCurrentPage(), p.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public Page<HistoryResponse> getHistory(UUID actorId, Pageable pageable) {
        Page<ReadingHistory> historyPage =
                historyRepository.findByUserIdOrderByReadAtDesc(actorId, pageable);

        Set<UUID> volumeIds = historyPage.getContent().stream()
                .map(ReadingHistory::getVolumeId)
                .collect(Collectors.toSet());

        Map<UUID, VolumeInfo> volumeInfos = volumeInfoUseCase.getInfoByIds(volumeIds);

        Set<UUID> mangaIds = volumeInfos.values().stream()
                .map(VolumeInfo::mangaId)
                .collect(Collectors.toSet());

        Map<UUID, MangaInfo> mangaInfos = mangaInfoUseCase.getInfoByIds(mangaIds);

        return historyPage.map(h -> {
            VolumeInfo vol = volumeInfos.get(h.getVolumeId());
            if (vol == null) return null;
            MangaInfo manga = mangaInfos.get(vol.mangaId());
            if (manga == null) return null;
            String coverUrl = manga.coverUrl() != null
                    ? storageClient.generateSignedUrl(manga.coverUrl(), Duration.ofHours(1)).toString()
                    : null;
            return new HistoryResponse(h.getVolumeId(), vol.volumeNumber(), vol.mangaId(),
                    manga.title(), coverUrl, h.getReadAt());
        });
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> getBatchProgress(List<UUID> volumeIds, UUID actorId) {
        return progressRepository.findByUserIdAndVolumeIdIn(actorId, volumeIds)
                .stream()
                .collect(Collectors.toMap(
                        ReadingProgress::getVolumeId,
                        ReadingProgress::getCurrentPage
                ));
    }

    @Transactional(readOnly = true)
    public Optional<ProgressResponse> findProgressByVolume(UUID volumeId, UUID actorId) {
        return progressRepository.findByUserIdAndVolumeId(actorId, volumeId)
                .map(p -> new ProgressResponse(p.getVolumeId(), p.getCurrentPage(), p.getUpdatedAt()));
    }
}
