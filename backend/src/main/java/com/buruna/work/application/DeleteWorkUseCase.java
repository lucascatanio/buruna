package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Apaga um mangá do catálogo e seus arquivos no storage. Posse "dono OU ADMIN" (ADR-35). */
@Service
public class DeleteWorkUseCase {

    private final WorkRepository workRepository;
    private final StorageClient storageClient;
    private final VolumeFileCleaner volumeFileCleaner;
    private final PublicWorkAccess access;

    public DeleteWorkUseCase(WorkRepository workRepository,
                              StorageClient storageClient,
                              VolumeFileCleaner volumeFileCleaner,
                              PublicWorkAccess access) {
        this.workRepository = workRepository;
        this.storageClient = storageClient;
        this.volumeFileCleaner = volumeFileCleaner;
        this.access = access;
    }

    @Transactional
    public void handle(UUID id, UUID actorId, boolean isAdmin) {
        Work work = access.findModifiable(id, actorId, isAdmin);

        work.getVolumes().forEach(volumeFileCleaner::delete);
        if (work.getCoverUrl() != null) {
            storageClient.delete(work.getCoverUrl());
        }

        workRepository.delete(work);
    }
}
