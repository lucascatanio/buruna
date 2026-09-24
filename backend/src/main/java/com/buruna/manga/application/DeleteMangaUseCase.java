package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Apaga um mangá do catálogo e seus arquivos no storage (volumes e capa). Posse
 * "dono OU ADMIN" (ADR-35). O arquivo de cada volume só é apagado se nenhum outro
 * volume ainda o referenciar (FIND-002, ver {@link VolumeFileCleaner}); a capa não
 * entra nessa regra — nunca teve objectName vindo do cliente.
 */
@Service
public class DeleteMangaUseCase {

    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;
    private final VolumeFileCleaner volumeFileCleaner;
    private final PublicMangaAccess access;

    public DeleteMangaUseCase(MangaRepository mangaRepository,
                              StorageClient storageClient,
                              VolumeFileCleaner volumeFileCleaner,
                              PublicMangaAccess access) {
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
        this.volumeFileCleaner = volumeFileCleaner;
        this.access = access;
    }

    @Transactional
    public void handle(UUID id, UUID actorId, boolean isAdmin) {
        Manga manga = access.findModifiable(id, actorId, isAdmin);

        manga.getVolumes().forEach(volumeFileCleaner::delete);
        if (manga.getCoverUrl() != null) {
            storageClient.delete(manga.getCoverUrl());
        }

        mangaRepository.delete(manga);
    }
}
