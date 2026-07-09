package com.buruna.engagement.application;

import com.buruna.engagement.domain.ReadingList;
import com.buruna.engagement.domain.ReadingListItemNotFoundException;
import com.buruna.engagement.persistence.ReadingListRepository;
import com.buruna.engagement.web.ReadingListRequest;
import com.buruna.engagement.web.ReadingListResponse;
import com.buruna.manga.application.FindPublicMangaUseCase;
import com.buruna.manga.application.GetMangaInfoUseCase;
import com.buruna.manga.application.MangaInfo;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReadingListService {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final ReadingListRepository readingListRepository;
    private final FindPublicMangaUseCase findPublicMangaUseCase;
    private final GetMangaInfoUseCase getMangaInfoUseCase;
    private final StorageClient storageClient;

    public ReadingListService(ReadingListRepository readingListRepository,
                              FindPublicMangaUseCase findPublicMangaUseCase,
                              GetMangaInfoUseCase getMangaInfoUseCase,
                              StorageClient storageClient) {
        this.readingListRepository = readingListRepository;
        this.findPublicMangaUseCase = findPublicMangaUseCase;
        this.getMangaInfoUseCase = getMangaInfoUseCase;
        this.storageClient = storageClient;
    }

    @Transactional(readOnly = true)
    public List<ReadingListResponse> findAll(UUID actorId) {
        List<ReadingList> entries = readingListRepository.findAllByUserIdOrderByUpdatedAtDesc(actorId);
        if (entries.isEmpty()) return List.of();

        Set<UUID> mangaIds = entries.stream().map(ReadingList::getMangaId).collect(Collectors.toSet());
        Map<UUID, MangaInfo> infoMap = getMangaInfoUseCase.getInfoByIds(mangaIds);

        return entries.stream()
                .map(rl -> toResponse(rl, infoMap.get(rl.getMangaId())))
                .toList();
    }

    @Transactional
    public ReadingListResponse upsert(UUID mangaId, ReadingListRequest request, UUID actorId) {
        MangaInfo info = findPublicMangaUseCase.getPublicMangaInfo(mangaId);

        ReadingList entry = readingListRepository
                .findByUserIdAndMangaId(actorId, mangaId)
                .orElseGet(() -> ReadingList.create(actorId, mangaId, request.status()));

        if (entry.getId() != null) {
            entry.updateStatus(request.status());
        }

        ReadingList saved = readingListRepository.save(entry);
        return toResponse(saved, info);
    }

    @Transactional
    public void remove(UUID mangaId, UUID actorId) {
        if (!readingListRepository.existsByUserIdAndMangaId(actorId, mangaId)) {
            throw new ReadingListItemNotFoundException(mangaId);
        }
        readingListRepository.deleteByUserIdAndMangaId(actorId, mangaId);
    }

    private ReadingListResponse toResponse(ReadingList rl, MangaInfo info) {
        String coverUrl = info.coverUrl() != null
                ? storageClient.generateSignedUrl(info.coverUrl(), COVER_URL_EXPIRATION).toString()
                : null;
        return new ReadingListResponse(
                info.id(),
                info.slug(),
                info.title(),
                coverUrl,
                rl.getStatus(),
                rl.getUpdatedAt()
        );
    }
}
