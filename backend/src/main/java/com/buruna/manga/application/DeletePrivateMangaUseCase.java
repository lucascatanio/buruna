package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Apaga um mangá privado do ator e seus arquivos no storage (capa + volumes). O
 * arquivo de cada volume só é apagado se nenhum outro volume ainda o referenciar
 * (FIND-002, ver {@link VolumeFileCleaner}); a capa não entra nessa regra.
 */
@Service
public class DeletePrivateMangaUseCase {

    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;
    private final VolumeFileCleaner volumeFileCleaner;
    private final PrivateMangaAccess access;

    public DeletePrivateMangaUseCase(MangaRepository mangaRepository,
                                     StorageClient storageClient,
                                     VolumeFileCleaner volumeFileCleaner,
                                     PrivateMangaAccess access) {
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
        this.volumeFileCleaner = volumeFileCleaner;
        this.access = access;
    }

    @Transactional
    public void handle(UUID id, UUID actorId) {
        Manga manga = access.findOwned(id, actorId);

        manga.getVolumes().forEach(volumeFileCleaner::delete);
        if (manga.getCoverUrl() != null) {
            storageClient.delete(manga.getCoverUrl());
        }

        mangaRepository.delete(manga);
    }
}
