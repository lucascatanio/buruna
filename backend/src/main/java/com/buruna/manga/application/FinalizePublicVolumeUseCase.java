package com.buruna.manga.application;

import com.buruna.manga.domain.DuplicateVolumeException;
import com.buruna.manga.domain.FileHash;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.domain.VolumeNumber;
import com.buruna.manga.domain.VolumeObjectName;
import com.buruna.manga.dto.VolumeFinalizeRequest;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.exception.PublicVolumeOnPrivateMangaException;
import com.buruna.manga.persistence.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Finaliza o upload (fase 2) de um volume de mangá público. Posse "dono OU ADMIN" (ADR-35);
 * mangá precisa ser público. Dedup por hash atravessa agregados (outros públicos) e fica na
 * application; dedup por número é invariante do agregado ({@code Manga.addVolume}).
 * {@code objectName} precisa ser um pendente do PRÓPRIO mangá (ADR-40, fecha o FIND-002).
 *
 * <p>RISCO residual (ADR-24, mitigado pelo ADR-40): objeto pendente órfão se o finalize
 * falhar após o upload; lifecycle rule do bucket apaga {@code pending/} após 1 dia.
 */
@Service
public class FinalizePublicVolumeUseCase {

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final PublicMangaAccess access;
    private final VolumeResponseMapper volumeResponseMapper;

    public FinalizePublicVolumeUseCase(VolumeRepository volumeRepository,
                                       StorageClient storageClient,
                                       PublicMangaAccess access,
                                       VolumeResponseMapper volumeResponseMapper) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.access = access;
        this.volumeResponseMapper = volumeResponseMapper;
    }

    @Transactional
    public VolumeResponse handle(UUID mangaId, VolumeFinalizeRequest request,
                                 UUID actorId, boolean isAdmin) {
        Manga manga = access.findModifiable(mangaId, actorId, isAdmin);
        if (!manga.isPublic()) {
            throw new PublicVolumeOnPrivateMangaException();
        }

        VolumeObjectName pending = VolumeObjectName.parsePending(request.objectName(), mangaId);
        var metadata = storageClient.getFileMetadata(request.objectName());

        // dedup por hash atravessa agregados (outros mangás públicos): permanece na application
        if (volumeRepository.existsByFileHashAndMangaIsPublicTrue(metadata.md5())) {
            throw new DuplicateVolumeException();
        }

        String finalObjectName = pending.finalObjectName();
        storageClient.move(request.objectName(), finalObjectName);

        Volume volume = manga.addVolume(
                VolumeNumber.of(request.volumeNumber()), finalObjectName,
                FileHash.of(metadata.md5()), metadata.size(), actorId);

        return volumeResponseMapper.toResponse(volumeRepository.save(volume));
    }
}
