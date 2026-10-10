package com.buruna.reading.application;

import java.util.Objects;
import java.util.HashSet;
import com.buruna.work.application.GetChapterForReadingUseCase;
import com.buruna.work.application.ChapterReadingView;
import com.buruna.work.application.ChapterInfo;
import com.buruna.work.application.ChapterAccessInfo;
import com.buruna.shared.storage.WindowedSignedUrls;
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
    private final GetChapterForReadingUseCase chapterForReading;
    private final WindowedSignedUrls windowedSignedUrls;

    public ReadingService(GetVolumeAccessUseCase volumeAccessUseCase,
                          GetVolumeInfoUseCase volumeInfoUseCase,
                          GetWorkInfoUseCase workInfoUseCase,
                          GetVolumeIdsByWorkUseCase volumeIdsByWorkUseCase,
                          ReadingProgressRepository progressRepository,
                          ReadingHistoryRepository historyRepository,
                          StorageClient storageClient,
                          GetChapterForReadingUseCase chapterForReading,
                          WindowedSignedUrls windowedSignedUrls) {
        this.volumeAccessUseCase = volumeAccessUseCase;
        this.volumeInfoUseCase = volumeInfoUseCase;
        this.workInfoUseCase = workInfoUseCase;
        this.volumeIdsByWorkUseCase = volumeIdsByWorkUseCase;
        this.progressRepository = progressRepository;
        this.historyRepository = historyRepository;
        this.storageClient = storageClient;
        this.chapterForReading = chapterForReading;
        this.windowedSignedUrls = windowedSignedUrls;
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
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> chapterIds = historyPage.getContent().stream()
                .map(ReadingHistory::getChapterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, VolumeInfo> volumeInfos = volumeInfoUseCase.getInfoByIds(volumeIds);
        Map<UUID, ChapterInfo> chapterInfos = chapterForReading.getInfoByIds(chapterIds);

        Set<UUID> workIds = new HashSet<>();
        volumeInfos.values().forEach(v -> workIds.add(v.workId()));
        chapterInfos.values().forEach(c -> workIds.add(c.workId()));
        Map<UUID, WorkInfo> workInfos = workInfoUseCase.getInfoByIds(workIds);

        return historyPage.map(h -> {
            if (h.getChapterId() != null) {
                ChapterInfo chapter = chapterInfos.get(h.getChapterId());
                WorkInfo work = chapter == null ? null : workInfos.get(chapter.workId());
                if (work == null) return null;
                return new HistoryResponse(null, null, chapter.chapterId(), chapter.number(), chapter.label(),
                        chapter.language(), chapter.workId(), work.title(), coverUrl(work), h.getReadAt());
            }
            VolumeInfo vol = volumeInfos.get(h.getVolumeId());
            if (vol == null) return null;
            WorkInfo work = workInfos.get(vol.workId());
            if (work == null) return null;
            return new HistoryResponse(h.getVolumeId(), vol.volumeNumber(), null, null, null, null,
                    vol.workId(), work.title(), coverUrl(work), h.getReadAt());
        });
    }

    private String coverUrl(WorkInfo work) {
        return work.coverUrl() != null
                ? storageClient.generateSignedUrl(work.coverUrl(), Duration.ofHours(1)).toString()
                : null;
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

    // ── Capítulos (ADR-44) ───────────────────────────────────────────────────

    /**
     * Abre um capítulo e devolve as páginas com URL assinada. Leitura de fato registra no
     * histórico e conta a visualização; o pré-carregamento do próximo capítulo ({@code prefetch})
     * não, para o histórico não ganhar um capítulo que o leitor nem abriu.
     */
    @Transactional
    public ChapterManifestResponse openChapter(UUID chapterId, UUID actorId, boolean prefetch) {
        ChapterReadingView chapter = chapterForReading.open(chapterId, actorId, !prefetch);

        if (!prefetch) {
            ReadingHistory entry = new ReadingHistory();
            entry.setUserId(actorId);
            entry.setChapterId(chapterId);
            historyRepository.save(entry);
        }

        List<ChapterManifestResponse.Page> pages = chapter.pages().stream()
                .map(p -> new ChapterManifestResponse.Page(
                        windowedSignedUrls.urlFor(p.objectName()).toString(),
                        p.dataSaverObjectName() == null ? null
                                : windowedSignedUrls.urlFor(p.dataSaverObjectName()).toString(),
                        p.width(), p.height()))
                .toList();
        return new ChapterManifestResponse(chapter.chapterId(), chapter.workId(), chapter.language(),
                chapter.number(), chapter.label(), chapter.title(), chapter.scanlationGroup(), pages,
                chapter.previousChapterId(), chapter.nextChapterId(), windowedSignedUrls.currentExpiry());
    }

    /** O total de páginas vem do capítulo, não do cliente. */
    @Transactional
    public ProgressResponse saveChapterProgress(UUID chapterId, int currentPage, UUID actorId) {
        ChapterAccessInfo access = chapterForReading.validateAccess(chapterId, actorId);

        ReadingProgress progress = progressRepository
                .findByUserIdAndChapterId(actorId, chapterId)
                .orElseGet(() -> ReadingProgress.startChapter(actorId, chapterId));

        progress.recordPage(currentPage, access.pageCount());
        progressRepository.saveAndFlush(progress);

        return ProgressResponse.from(progress);
    }

    @Transactional(readOnly = true)
    public Optional<ProgressResponse> findChapterProgress(UUID chapterId, UUID actorId) {
        chapterForReading.validateAccess(chapterId, actorId);
        return progressRepository.findByUserIdAndChapterId(actorId, chapterId)
                .map(ProgressResponse::from);
    }

    /**
     * Progresso do leitor em todos os capítulos publicados de uma obra, do mais recente para o
     * mais antigo: o primeiro é o "continuar lendo", e o conjunto marca o que já foi lido.
     */
    @Transactional(readOnly = true)
    public List<ProgressResponse> getWorkChapterProgress(UUID workId, UUID actorId) {
        List<UUID> chapterIds = chapterForReading.publishedChapterIds(workId, actorId);
        if (chapterIds.isEmpty()) {
            return List.of();
        }
        return progressRepository.findByUserIdAndChapterIdIn(actorId, chapterIds).stream()
                .sorted(Comparator.comparing(ReadingProgress::getUpdatedAt).reversed())
                .map(ProgressResponse::from)
                .toList();
    }
}
