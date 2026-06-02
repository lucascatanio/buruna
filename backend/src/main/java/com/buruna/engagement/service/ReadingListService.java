package com.buruna.engagement.service;

import com.buruna.engagement.domain.ReadingList;
import com.buruna.engagement.dto.ReadingListRequest;
import com.buruna.engagement.dto.ReadingListResponse;
import com.buruna.engagement.repository.ReadingListRepository;
import com.buruna.shared.exception.LegacyHttpDomainException;
import com.buruna.shared.storage.StorageClient;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.user.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class ReadingListService {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final ReadingListRepository readingListRepository;
    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;

    public ReadingListService(ReadingListRepository readingListRepository,
                              MangaRepository mangaRepository,
                              StorageClient storageClient) {
        this.readingListRepository = readingListRepository;
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
    }

    @Transactional(readOnly = true)
    public List<ReadingListResponse> findAll(User user) {
        return readingListRepository.findAllByUserIdWithManga(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReadingListResponse upsert(UUID mangaId, ReadingListRequest request, User user) {
        Manga manga = mangaRepository.findById(mangaId)
                .filter(Manga::isPublic)
                .orElseThrow(() -> new LegacyHttpDomainException(HttpStatus.NOT_FOUND, "Mangá não encontrado"));

        ReadingList entry = readingListRepository
                .findByUserIdAndMangaId(user.getId(), mangaId)
                .orElseGet(() -> {
                    ReadingList rl = new ReadingList();
                    rl.setUser(user);
                    rl.setManga(manga);
                    return rl;
                });

        entry.setStatus(request.status());
        return toResponse(readingListRepository.save(entry));
    }

    @Transactional
    public void remove(UUID mangaId, User user) {
        if (!readingListRepository.existsByUserIdAndMangaId(user.getId(), mangaId)) {
            throw new LegacyHttpDomainException(HttpStatus.NOT_FOUND, "Item não encontrado na lista de leitura");
        }
        readingListRepository.deleteByUserIdAndMangaId(user.getId(), mangaId);
    }

    private ReadingListResponse toResponse(ReadingList rl) {
        Manga manga = rl.getManga();
        String coverUrl = manga.getCoverUrl() != null
                ? storageClient.generateSignedUrl(manga.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;
        return new ReadingListResponse(
                manga.getId(),
                manga.getSlug(),
                manga.getTitle(),
                coverUrl,
                rl.getStatus(),
                rl.getUpdatedAt()
        );
    }
}