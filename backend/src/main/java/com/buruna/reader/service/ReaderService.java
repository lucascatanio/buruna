package com.buruna.reader.service;

import com.buruna.infra.exception.DomainException;
import com.buruna.infra.storage.StorageClient;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.reader.domain.ReadingHistory;
import com.buruna.reader.domain.ReadingProgress;
import com.buruna.reader.dto.HistoryResponse;
import com.buruna.reader.dto.ProgressRequest;
import com.buruna.reader.dto.ProgressResponse;
import com.buruna.reader.dto.VolumeUrlResponse;
import com.buruna.reader.repository.ReadingHistoryRepository;
import com.buruna.reader.repository.ReadingProgressRepository;
import com.buruna.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReaderService {

    private static final int SIGNED_URL_EXPIRATION_SECONDS = 1800; // 30 min
    private static final Duration SIGNED_URL_DURATION = Duration.ofSeconds(SIGNED_URL_EXPIRATION_SECONDS);

    private final VolumeRepository volumeRepository;
    private final MangaRepository mangaRepository;
    private final ReadingProgressRepository progressRepository;
    private final ReadingHistoryRepository historyRepository;
    private final StorageClient storageClient;

    public ReaderService(VolumeRepository volumeRepository,
                         MangaRepository mangaRepository,
                         ReadingProgressRepository progressRepository,
                         ReadingHistoryRepository historyRepository,
                         StorageClient storageClient) {
        this.volumeRepository = volumeRepository;
        this.mangaRepository = mangaRepository;
        this.progressRepository = progressRepository;
        this.historyRepository = historyRepository;
        this.storageClient = storageClient;
    }

    @Transactional
    public VolumeUrlResponse getVolumeUrl(UUID volumeId, User user) {
        Volume volume = findAccessibleVolume(volumeId, user);

        Manga manga = volume.getManga();
        manga.setViewCount(manga.getViewCount() + 1);
        mangaRepository.save(manga);

        ReadingHistory history = new ReadingHistory();
        history.setUser(user);
        history.setVolume(volume);
        historyRepository.save(history);

        String signedUrl = storageClient
                .generateSignedUrl(volume.getFileUrl(), SIGNED_URL_DURATION)
                .toString();

        return new VolumeUrlResponse(volumeId, signedUrl, SIGNED_URL_EXPIRATION_SECONDS);
    }

    @Transactional
    public ProgressResponse saveProgress(UUID volumeId, ProgressRequest request, User user) {
        Volume volume = findAccessibleVolume(volumeId, user);

        ReadingProgress progress = progressRepository
                .findByUserIdAndVolumeId(user.getId(), volumeId)
                .orElseGet(() -> {
                    ReadingProgress p = new ReadingProgress();
                    p.setUser(user);
                    p.setVolume(volume);
                    return p;
                });

        progress.setCurrentPage(request.currentPage());
        progressRepository.save(progress);

        return new ProgressResponse(volumeId, progress.getCurrentPage(), progress.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(UUID mangaId, User user) {
        mangaRepository.findById(mangaId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Mangá não encontrado"));

        return progressRepository
                .findLatestByUserIdAndMangaId(user.getId(), mangaId)
                .map(p -> new ProgressResponse(
                        p.getVolume().getId(),
                        p.getCurrentPage(),
                        p.getUpdatedAt()))
                .orElse(null); // null = nunca leu, frontend trata como início
    }

    @Transactional(readOnly = true)
    public Page<HistoryResponse> getHistory(User user, Pageable pageable) {
        return historyRepository
                .findByUserIdOrderByReadAtDesc(user.getId(), pageable)
                .map(h -> {
                    Volume v = h.getVolume();
                    Manga m = v.getManga();
                    String coverUrl = m.getCoverUrl() != null
                            ? storageClient.generateSignedUrl(m.getCoverUrl(), Duration.ofHours(1)).toString()
                            : null;
                    return new HistoryResponse(
                            v.getId(),
                            v.getVolumeNumber(),
                            m.getId(),
                            m.getTitle(),
                            coverUrl,
                            h.getReadAt());
                });
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> getBatchProgress(List<UUID> volumeIds, User user) {
        return progressRepository
                .findByUserIdAndVolumeIdIn(user.getId(), volumeIds)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getVolume().getId(),
                        ReadingProgress::getCurrentPage
                ));
    }

    // verifica acesso: volume público (qualquer auth) ou volume privado (só owner)
    private Volume findAccessibleVolume(UUID volumeId, User user) {
        Volume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Volume não encontrado"));

        Manga manga = volume.getManga();

        if (!manga.isPublic() && !manga.getOwner().getId().equals(user.getId())) {
            throw new DomainException(HttpStatus.FORBIDDEN,
                    "Você não tem acesso a este volume");
        }

        return volume;
    }

    public Optional<ProgressResponse> findProgressByVolume(UUID volumeId, User user) {
        return progressRepository
                .findByUserIdAndVolumeId(user.getId(), volumeId)
                .map(p -> new ProgressResponse(
                        p.getVolume().getId(),
                        p.getCurrentPage(),
                        p.getUpdatedAt()
                ));
    }
}