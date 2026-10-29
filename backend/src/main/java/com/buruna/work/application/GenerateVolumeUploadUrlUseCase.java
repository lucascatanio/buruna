package com.buruna.work.application;

import com.buruna.work.domain.DuplicateVolumeException;
import com.buruna.work.domain.VolumeObjectName;
import com.buruna.work.persistence.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Gera a URL assinada de upload de um volume (fase 1 do upload em 2 fases). A dedup por
 * número já acontece aqui; a cota e o hash são validados no finalize (fase 2).
 */
@Service
public class GenerateVolumeUploadUrlUseCase {

    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(15);
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final PrivateWorkAccess access;

    public GenerateVolumeUploadUrlUseCase(VolumeRepository volumeRepository,
                                          StorageClient storageClient,
                                          PrivateWorkAccess access) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public VolumeUploadUrlResponse handle(UUID workId, Integer volumeNumber, UUID actorId) {
        access.findOwned(workId, actorId);

        if (volumeRepository.existsByWorkIdAndVolumeNumber(workId, volumeNumber)) {
            throw new DuplicateVolumeException(volumeNumber);
        }

        String objectName = VolumeObjectName.pendingFor(workId);
        var signedUpload = storageClient.generateUploadSignedUrl(objectName, PDF_CONTENT_TYPE, UPLOAD_URL_EXPIRATION);

        return new VolumeUploadUrlResponse(
                signedUpload.url().toString(), objectName, signedUpload.requiredHeaders());
    }
}
