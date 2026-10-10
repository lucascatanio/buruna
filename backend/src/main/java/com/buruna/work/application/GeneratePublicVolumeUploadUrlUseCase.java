package com.buruna.work.application;

import com.buruna.work.domain.DuplicateVolumeException;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.VolumeObjectName;
import com.buruna.work.domain.PublicVolumeOnPrivateWorkException;
import com.buruna.work.persistence.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Gera a URL assinada de upload (fase 1) de um volume de mangá público. Posse "dono OU
 * ADMIN" (ADR-35); o mangá precisa ser público (privados usam /my/works).
 */
@Service
public class GeneratePublicVolumeUploadUrlUseCase {

    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(15);
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final PublicWorkAccess access;

    public GeneratePublicVolumeUploadUrlUseCase(VolumeRepository volumeRepository,
                                                StorageClient storageClient,
                                                PublicWorkAccess access) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public VolumeUploadUrlResponse handle(UUID workId, Integer volumeNumber,
                                          UUID actorId, boolean isAdmin) {
        Work work = access.findModifiable(workId, actorId, isAdmin);
        if (!work.isPublic()) {
            throw new PublicVolumeOnPrivateWorkException();
        }

        if (volumeRepository.existsByWorkIdAndVolumeNumber(workId, volumeNumber)) {
            throw new DuplicateVolumeException(volumeNumber);
        }

        String objectName = VolumeObjectName.pendingFor(workId);
        var signedUpload = storageClient.generateUploadSignedUrl(objectName, PDF_CONTENT_TYPE, UPLOAD_URL_EXPIRATION);

        return new VolumeUploadUrlResponse(
                signedUpload.url().toString(), objectName, signedUpload.requiredHeaders());
    }
}
