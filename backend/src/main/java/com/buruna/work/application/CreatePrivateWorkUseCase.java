package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.shared.storage.StorageClient;
import com.buruna.shared.storage.StorageUploadHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cria um mangá na coleção privada do ator (ADR-35: posse por actorId). */
@Service
public class CreatePrivateWorkUseCase {

    private final WorkRepository workRepository;
    private final StorageClient storageClient;
    private final SlugAllocator slugAllocator;
    private final PrivateWorkMapper mapper;

    public CreatePrivateWorkUseCase(WorkRepository workRepository,
                                     StorageClient storageClient,
                                     SlugAllocator slugAllocator,
                                     PrivateWorkMapper mapper) {
        this.workRepository = workRepository;
        this.storageClient = storageClient;
        this.slugAllocator = slugAllocator;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateWorkResponse handle(String title, String synopsis, String coverBase64, UUID actorId) {
        Work work = Work.createPrivate(slugAllocator.allocate(title), title, synopsis, actorId);

        if (coverBase64 != null && !coverBase64.isBlank()) {
            String coverObjectName = StorageUploadHelper.uploadBase64Image(
                    storageClient, coverBase64, "covers");
            work.changeCover(coverObjectName);
        }

        return mapper.toResponse(workRepository.save(work));
    }
}
