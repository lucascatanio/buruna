package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Remove um volume de um mangá público e apaga o arquivo. Posse "dono OU ADMIN" (ADR-35). */
@Service
public class DeletePublicVolumeUseCase {

    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;
    private final PublicMangaAccess access;

    public DeletePublicVolumeUseCase(MangaRepository mangaRepository,
                                     StorageClient storageClient,
                                     PublicMangaAccess access) {
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
        this.access = access;
    }

    @Transactional
    public void handle(UUID mangaId, UUID volumeId, UUID actorId, boolean isAdmin) {
        Manga manga = access.findModifiable(mangaId, actorId, isAdmin);
        Volume volume = manga.removeVolume(volumeId);
        storageClient.delete(volume.getFileUrl());
        mangaRepository.save(manga);
    }
}
