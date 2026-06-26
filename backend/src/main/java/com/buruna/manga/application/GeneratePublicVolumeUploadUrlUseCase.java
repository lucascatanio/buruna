package com.buruna.manga.application;

import com.buruna.manga.domain.DuplicateVolumeException;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.VolumeUploadUrlResponse;
import com.buruna.manga.exception.PublicVolumeOnPrivateMangaException;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Gera a URL assinada de upload (fase 1) de um volume de mangá público. Posse "dono OU
 * ADMIN" (ADR-35); o mangá precisa ser público (privados usam /my/mangas).
 */
@Service
public class GeneratePublicVolumeUploadUrlUseCase {

    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(15);

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final PublicMangaAccess access;

    public GeneratePublicVolumeUploadUrlUseCase(VolumeRepository volumeRepository,
                                                StorageClient storageClient,
                                                PublicMangaAccess access) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public VolumeUploadUrlResponse handle(UUID mangaId, Integer volumeNumber,
                                          UUID actorId, boolean isAdmin) {
        Manga manga = access.findModifiable(mangaId, actorId, isAdmin);
        if (!manga.isPublic()) {
            throw new PublicVolumeOnPrivateMangaException();
        }

        if (volumeRepository.existsByMangaIdAndVolumeNumber(mangaId, volumeNumber)) {
            throw new DuplicateVolumeException(volumeNumber);
        }

        String objectName = "volumes/" + UUID.randomUUID() + ".pdf";
        var uploadUrl = storageClient.generateUploadSignedUrl(objectName, UPLOAD_URL_EXPIRATION);

        return new VolumeUploadUrlResponse(uploadUrl.toString(), objectName);
    }
}
