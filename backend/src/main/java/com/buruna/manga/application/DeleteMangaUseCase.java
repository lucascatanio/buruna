package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Apaga um mangá do catálogo e seus arquivos no storage. Posse "dono OU ADMIN" (ADR-35). */
@Service
public class DeleteMangaUseCase {

    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;
    private final PublicMangaAccess access;

    public DeleteMangaUseCase(MangaRepository mangaRepository,
                              StorageClient storageClient,
                              PublicMangaAccess access) {
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
        this.access = access;
    }

    @Transactional
    public void handle(UUID id, UUID actorId, boolean isAdmin) {
        Manga manga = access.findModifiable(id, actorId, isAdmin);

        manga.getVolumes().forEach(v -> storageClient.delete(v.getFileUrl()));
        if (manga.getCoverUrl() != null) {
            storageClient.delete(manga.getCoverUrl());
        }

        mangaRepository.delete(manga);
    }
}
