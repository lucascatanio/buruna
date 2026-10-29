package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.Volume;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Remove um volume de um mangá público e apaga o arquivo. Posse "dono OU ADMIN" (ADR-35). */
@Service
public class DeletePublicVolumeUseCase {

    private final WorkRepository workRepository;
    private final VolumeFileCleaner volumeFileCleaner;
    private final PublicWorkAccess access;

    public DeletePublicVolumeUseCase(WorkRepository workRepository,
                                     VolumeFileCleaner volumeFileCleaner,
                                     PublicWorkAccess access) {
        this.workRepository = workRepository;
        this.volumeFileCleaner = volumeFileCleaner;
        this.access = access;
    }

    @Transactional
    public void handle(UUID workId, UUID volumeId, UUID actorId, boolean isAdmin) {
        Work work = access.findModifiable(workId, actorId, isAdmin);
        Volume volume = work.removeVolume(volumeId);
        volumeFileCleaner.delete(volume);
        workRepository.save(work);
    }
}
