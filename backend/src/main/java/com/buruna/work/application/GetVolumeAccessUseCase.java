package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.Volume;
import com.buruna.work.domain.VolumeNotFoundException;
import com.buruna.work.domain.VolumeAccessDeniedException;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.work.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Porta pública do contexto work para o contexto reading (ADR-35).
 * Verifica acesso a volumes (público OU dono) e, em openVolume, incrementa view_count.
 */
@Service
public class GetVolumeAccessUseCase {

    private final VolumeRepository volumeRepository;
    private final WorkRepository workRepository;

    public GetVolumeAccessUseCase(VolumeRepository volumeRepository,
                                  WorkRepository workRepository) {
        this.volumeRepository = volumeRepository;
        this.workRepository = workRepository;
    }

    /** Verifica acesso e incrementa view_count — para abertura de leitura. */
    @Transactional
    public VolumeReadInfo openVolume(UUID volumeId, UUID actorId) {
        Volume volume = loadAndCheck(volumeId, actorId);
        Work work = volume.getWork();
        work.registerView();
        workRepository.save(work);
        return new VolumeReadInfo(volume.getId(), volume.getFileUrl(), work.getId());
    }

    /** Verifica acesso sem side-effects — para salvar progresso. */
    @Transactional(readOnly = true)
    public VolumeReadInfo validateAccess(UUID volumeId, UUID actorId) {
        Volume volume = loadAndCheck(volumeId, actorId);
        return new VolumeReadInfo(volume.getId(), volume.getFileUrl(), volume.getWork().getId());
    }

    private Volume loadAndCheck(UUID volumeId, UUID actorId) {
        Volume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new VolumeNotFoundException(volumeId));
        Work work = volume.getWork();
        if (!work.isPublic() && !work.getOwnerId().equals(actorId)) {
            throw new VolumeAccessDeniedException(volumeId);
        }
        return volume;
    }
}
