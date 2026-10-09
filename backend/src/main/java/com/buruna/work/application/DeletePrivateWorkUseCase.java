package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Apaga um mangá privado do ator e seus arquivos no storage (capa + volumes). */
@Service
public class DeletePrivateWorkUseCase {

    private final WorkRepository workRepository;
    private final StorageClient storageClient;
    private final VolumeFileCleaner volumeFileCleaner;
    private final PrivateWorkAccess access;

    public DeletePrivateWorkUseCase(WorkRepository workRepository,
                                     StorageClient storageClient,
                                     VolumeFileCleaner volumeFileCleaner,
                                     PrivateWorkAccess access) {
        this.workRepository = workRepository;
        this.storageClient = storageClient;
        this.volumeFileCleaner = volumeFileCleaner;
        this.access = access;
    }

    @Transactional
    public void handle(UUID id, UUID actorId) {
        Work work = access.findOwned(id, actorId);

        work.getVolumes().forEach(volumeFileCleaner::delete);
        if (work.getCoverUrl() != null) {
            storageClient.delete(work.getCoverUrl());
        }

        workRepository.delete(work);
    }
}
