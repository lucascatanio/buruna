package com.buruna.manga.application;

import com.buruna.manga.domain.DuplicateVolumeException;
import com.buruna.manga.dto.VolumeUploadUrlResponse;
import com.buruna.manga.repository.VolumeRepository;
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

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final PrivateMangaAccess access;

    public GenerateVolumeUploadUrlUseCase(VolumeRepository volumeRepository,
                                          StorageClient storageClient,
                                          PrivateMangaAccess access) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public VolumeUploadUrlResponse handle(UUID mangaId, Integer volumeNumber, UUID actorId) {
        access.findOwned(mangaId, actorId);

        if (volumeRepository.existsByMangaIdAndVolumeNumber(mangaId, volumeNumber)) {
            throw new DuplicateVolumeException(volumeNumber);
        }

        String objectName = "volumes/" + UUID.randomUUID() + ".pdf";
        var uploadUrl = storageClient.generateUploadSignedUrl(objectName, UPLOAD_URL_EXPIRATION);

        return new VolumeUploadUrlResponse(uploadUrl.toString(), objectName);
    }
}
