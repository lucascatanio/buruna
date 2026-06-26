package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Apaga um mangá privado do ator e seus arquivos no storage (capa + volumes). */
@Service
public class DeletePrivateMangaUseCase {

    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;
    private final PrivateMangaAccess access;

    public DeletePrivateMangaUseCase(MangaRepository mangaRepository,
                                     StorageClient storageClient,
                                     PrivateMangaAccess access) {
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
        this.access = access;
    }

    @Transactional
    public void handle(UUID id, UUID actorId) {
        Manga manga = access.findOwned(id, actorId);

        manga.getVolumes().forEach(v -> storageClient.delete(v.getFileUrl()));
        if (manga.getCoverUrl() != null) {
            storageClient.delete(manga.getCoverUrl());
        }

        mangaRepository.delete(manga);
    }
}
