package com.buruna.work.application;

import com.buruna.work.domain.DuplicateVolumeException;
import com.buruna.work.domain.FileHash;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.PendingUploadNotFoundException;
import com.buruna.work.domain.Volume;
import com.buruna.work.domain.VolumeNumber;
import com.buruna.work.domain.VolumeObjectName;
import com.buruna.work.domain.PublicVolumeOnPrivateWorkException;
import com.buruna.work.persistence.VolumeRepository;
import com.buruna.shared.exception.StorageObjectNotFoundException;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Finaliza o upload (fase 2) de um volume de mangá público. Posse "dono OU ADMIN" (ADR-35);
 * mangá precisa ser público. Dedup por hash atravessa agregados (outros públicos) e fica na
 * application; dedup por número é invariante do agregado ({@code Work.addVolume}).
 * {@code objectName} precisa ser um pendente do PRÓPRIO mangá (ADR-40).
 *
 * <p>RISCO residual (ADR-24, mitigado pelo ADR-40): objeto pendente órfão se o finalize
 * falhar após o upload; lifecycle rule do bucket apaga {@code pending/} após 1 dia.
 */
@Service
public class FinalizePublicVolumeUseCase {

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final PublicWorkAccess access;
    private final VolumeResponseMapper volumeResponseMapper;

    public FinalizePublicVolumeUseCase(VolumeRepository volumeRepository,
                                       StorageClient storageClient,
                                       PublicWorkAccess access,
                                       VolumeResponseMapper volumeResponseMapper) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.access = access;
        this.volumeResponseMapper = volumeResponseMapper;
    }

    @Transactional
    public VolumeResponse handle(UUID workId, VolumeFinalizeRequest request,
                                 UUID actorId, boolean isAdmin) {
        Work work = access.findModifiable(workId, actorId, isAdmin);
        if (!work.isPublic()) {
            throw new PublicVolumeOnPrivateWorkException();
        }

        VolumeObjectName pending = VolumeObjectName.parsePending(request.objectName(), workId);
        // objeto ausente = finalize repetido, perdedor de finalize concorrente ou upload
        // nunca feito: 404, não 500
        try {
            var metadata = storageClient.getFileMetadata(request.objectName());

            // dedup por hash atravessa agregados (outros mangás públicos): permanece na application
            if (volumeRepository.existsByFileHashAndWorkIsPublicTrue(metadata.md5())) {
                throw new DuplicateVolumeException();
            }

            // invariantes do agregado antes do move: se addVolume lançar, o objeto continua em
            // pending/ (limpo pela lifecycle rule) em vez de virar órfão em volumes/
            String finalObjectName = pending.finalObjectName();
            Volume volume = work.addVolume(
                    VolumeNumber.of(request.volumeNumber()), finalObjectName,
                    FileHash.of(metadata.md5()), metadata.size(), actorId);

            storageClient.move(request.objectName(), finalObjectName);

            return volumeResponseMapper.toResponse(volumeRepository.save(volume));
        } catch (StorageObjectNotFoundException e) {
            throw new PendingUploadNotFoundException();
        }
    }
}
