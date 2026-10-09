package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.Volume;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Remove um volume do agregado privado e apaga o arquivo no storage. */
@Service
public class DeleteVolumeUseCase {

    private final WorkRepository workRepository;
    private final VolumeFileCleaner volumeFileCleaner;
    private final PrivateWorkAccess access;
    private final PrivateWorkMapper mapper;

    public DeleteVolumeUseCase(WorkRepository workRepository,
                               VolumeFileCleaner volumeFileCleaner,
                               PrivateWorkAccess access,
                               PrivateWorkMapper mapper) {
        this.workRepository = workRepository;
        this.volumeFileCleaner = volumeFileCleaner;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateWorkResponse handle(UUID workId, UUID volumeId, UUID actorId) {
        Work work = access.findOwned(workId, actorId);
        Volume volume = work.removeVolume(volumeId);
        volumeFileCleaner.delete(volume);
        workRepository.save(work);
        return mapper.toResponse(work);
    }
}
