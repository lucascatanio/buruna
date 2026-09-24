package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Remove um volume do agregado privado e apaga o arquivo no storage. */
@Service
public class DeleteVolumeUseCase {

    private final MangaRepository mangaRepository;
    private final VolumeFileCleaner volumeFileCleaner;
    private final PrivateMangaAccess access;
    private final PrivateMangaMapper mapper;

    public DeleteVolumeUseCase(MangaRepository mangaRepository,
                               VolumeFileCleaner volumeFileCleaner,
                               PrivateMangaAccess access,
                               PrivateMangaMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.volumeFileCleaner = volumeFileCleaner;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateMangaResponse handle(UUID mangaId, UUID volumeId, UUID actorId) {
        Manga manga = access.findOwned(mangaId, actorId);
        Volume volume = manga.removeVolume(volumeId);
        volumeFileCleaner.delete(volume);
        mangaRepository.save(manga);
        return mapper.toResponse(manga);
    }
}
