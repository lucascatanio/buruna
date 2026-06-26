package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.shared.storage.StorageClient;
import com.buruna.shared.storage.StorageUploadHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cria um mangá na coleção privada do ator (ADR-35: posse por actorId). */
@Service
public class CreatePrivateMangaUseCase {

    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;
    private final SlugAllocator slugAllocator;
    private final PrivateMangaMapper mapper;

    public CreatePrivateMangaUseCase(MangaRepository mangaRepository,
                                     StorageClient storageClient,
                                     SlugAllocator slugAllocator,
                                     PrivateMangaMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
        this.slugAllocator = slugAllocator;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateMangaResponse handle(String title, String synopsis, String coverBase64, UUID actorId) {
        Manga manga = Manga.createPrivate(slugAllocator.allocate(title), title, synopsis, actorId);

        if (coverBase64 != null && !coverBase64.isBlank()) {
            String coverObjectName = StorageUploadHelper.uploadBase64Image(
                    storageClient, coverBase64, "covers");
            manga.changeCover(coverObjectName);
        }

        return mapper.toResponse(mangaRepository.save(manga));
    }
}
