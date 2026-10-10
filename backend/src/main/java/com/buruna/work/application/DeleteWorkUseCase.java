package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Apaga um mangá do catálogo e seus arquivos no storage. Posse "dono OU ADMIN" (ADR-35). */
@Service
public class DeleteWorkUseCase {

    private final WorkRepository workRepository;
    private final StorageClient storageClient;
    private final VolumeFileCleaner volumeFileCleaner;
    private final ChapterStorageCleaner chapterStorageCleaner;
    private final PublicWorkAccess access;

    public DeleteWorkUseCase(WorkRepository workRepository,
                              StorageClient storageClient,
                              VolumeFileCleaner volumeFileCleaner,
                              ChapterStorageCleaner chapterStorageCleaner,
                              PublicWorkAccess access) {
        this.workRepository = workRepository;
        this.storageClient = storageClient;
        this.volumeFileCleaner = volumeFileCleaner;
        this.chapterStorageCleaner = chapterStorageCleaner;
        this.access = access;
    }

    @Transactional
    public void handle(UUID id, UUID actorId, boolean isAdmin) {
        Work work = access.findModifiable(id, actorId, isAdmin);

        // capítulos saem do banco por cascata (V28); os objetos deles, depois do commit
        List<String> chapterObjects = chapterStorageCleaner.objectNamesOfWorks(List.of(work.getId()));
        AfterCommit.run(() -> chapterStorageCleaner.deleteQuietly(chapterObjects));
        work.getVolumes().forEach(volumeFileCleaner::delete);
        if (work.getCoverUrl() != null) {
            storageClient.delete(work.getCoverUrl());
        }

        workRepository.delete(work);
    }
}
