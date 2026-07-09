package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.domain.VolumeNotFoundException;
import com.buruna.manga.exception.VolumeAccessDeniedException;
import com.buruna.manga.persistence.MangaRepository;
import com.buruna.manga.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Porta pública do contexto manga para o contexto reading (ADR-35).
 * Verifica acesso a volumes (público OU dono) e, em openVolume, incrementa view_count.
 */
@Service
public class GetVolumeAccessUseCase {

    private final VolumeRepository volumeRepository;
    private final MangaRepository mangaRepository;

    public GetVolumeAccessUseCase(VolumeRepository volumeRepository,
                                  MangaRepository mangaRepository) {
        this.volumeRepository = volumeRepository;
        this.mangaRepository = mangaRepository;
    }

    /** Verifica acesso e incrementa view_count — para abertura de leitura. */
    @Transactional
    public VolumeReadInfo openVolume(UUID volumeId, UUID actorId) {
        Volume volume = loadAndCheck(volumeId, actorId);
        Manga manga = volume.getManga();
        manga.registerView();
        mangaRepository.save(manga);
        return new VolumeReadInfo(volume.getId(), volume.getFileUrl(), manga.getId());
    }

    /** Verifica acesso sem side-effects — para salvar progresso. */
    @Transactional(readOnly = true)
    public VolumeReadInfo validateAccess(UUID volumeId, UUID actorId) {
        Volume volume = loadAndCheck(volumeId, actorId);
        return new VolumeReadInfo(volume.getId(), volume.getFileUrl(), volume.getManga().getId());
    }

    private Volume loadAndCheck(UUID volumeId, UUID actorId) {
        Volume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new VolumeNotFoundException(volumeId));
        Manga manga = volume.getManga();
        if (!manga.isPublic() && !manga.getOwnerId().equals(actorId)) {
            throw new VolumeAccessDeniedException(volumeId);
        }
        return volume;
    }
}
