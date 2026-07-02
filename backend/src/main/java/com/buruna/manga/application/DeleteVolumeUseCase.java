package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Remove um volume do agregado privado e apaga o arquivo no storage. */
@Service
public class DeleteVolumeUseCase {

    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;
    private final PrivateMangaAccess access;
    private final PrivateMangaMapper mapper;

    public DeleteVolumeUseCase(MangaRepository mangaRepository,
                               StorageClient storageClient,
                               PrivateMangaAccess access,
                               PrivateMangaMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateMangaResponse handle(UUID mangaId, UUID volumeId, UUID actorId) {
        Manga manga = access.findOwned(mangaId, actorId);
        Volume volume = manga.removeVolume(volumeId);
        storageClient.delete(volume.getFileUrl());
        mangaRepository.save(manga);
        return mapper.toResponse(manga);
    }
}
