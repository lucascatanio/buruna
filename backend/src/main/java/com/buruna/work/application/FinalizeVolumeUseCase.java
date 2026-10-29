package com.buruna.work.application;

import com.buruna.work.domain.FileHash;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.PendingUploadNotFoundException;
import com.buruna.work.domain.Volume;
import com.buruna.work.domain.VolumeNumber;
import com.buruna.work.domain.VolumeObjectName;
import com.buruna.work.persistence.VolumeRepository;
import com.buruna.shared.exception.StorageObjectNotFoundException;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Finaliza o upload de um volume (fase 2): valida que o {@code objectName} é um
 * pendente do PRÓPRIO mangá (ADR-40), lê o metadado do objeto no
 * storage, valida a cota, move o objeto de {@code pending/} para o nome definitivo e
 * adiciona o volume ao agregado. O limite de cota ({@code quotaGb}) chega como
 * primitivo da borda (ADR-35).
 *
 * <p>RISCO residual (ADR-24, mitigado pelo ADR-40): se o finalize falhar após o upload
 * no storage, o objeto pendente fica órfão em {@code pending/}. A lifecycle rule do
 * bucket (passo manual, ADR-40) apaga esse prefixo após 1 dia.
 */
@Service
public class FinalizeVolumeUseCase {

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final QuotaService quotaService;
    private final PrivateWorkAccess access;
    private final PrivateWorkMapper mapper;

    public FinalizeVolumeUseCase(VolumeRepository volumeRepository,
                                 StorageClient storageClient,
                                 QuotaService quotaService,
                                 PrivateWorkAccess access,
                                 PrivateWorkMapper mapper) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.quotaService = quotaService;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateWorkResponse handle(UUID workId, VolumeFinalizeRequest request,
                                       UUID actorId, BigDecimal quotaGb) {
        Work work = access.findOwned(workId, actorId);

        VolumeObjectName pending = VolumeObjectName.parsePending(request.objectName(), workId);
        // objeto ausente = finalize repetido, perdedor de finalize concorrente ou upload
        // nunca feito: 404, não 500
        try {
            var metadata = storageClient.getFileMetadata(request.objectName());

            quotaService.assertCanFit(actorId, quotaGb, metadata.size());

            // invariantes do agregado antes do move: se addVolume lançar, o objeto continua em
            // pending/ (limpo pela lifecycle rule) em vez de virar órfão em volumes/
            String finalObjectName = pending.finalObjectName();
            Volume volume = work.addVolume(
                    VolumeNumber.of(request.volumeNumber()), finalObjectName,
                    FileHash.of(metadata.md5()), metadata.size(), actorId);

            storageClient.move(request.objectName(), finalObjectName);
            volumeRepository.save(volume);
        } catch (StorageObjectNotFoundException e) {
            throw new PendingUploadNotFoundException();
        }

        return mapper.toResponse(work);
    }
}
