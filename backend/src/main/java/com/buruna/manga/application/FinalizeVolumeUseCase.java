package com.buruna.manga.application;

import com.buruna.manga.domain.FileHash;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.domain.VolumeNumber;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.dto.VolumeFinalizeRequest;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Finaliza o upload de um volume (fase 2): lê o metadado do objeto no storage, valida a
 * cota e adiciona o volume ao agregado. O limite de cota ({@code quotaGb}) chega como
 * primitivo da borda (ADR-35).
 *
 * <p>RISCO (ADR-24): se o finalize falhar após o upload no storage, o arquivo fica órfão
 * no bucket. Mitigação futura: lifecycle rule de 24h para objetos sem registro no banco.
 */
@Service
public class FinalizeVolumeUseCase {

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final QuotaService quotaService;
    private final PrivateMangaAccess access;
    private final PrivateMangaMapper mapper;

    public FinalizeVolumeUseCase(VolumeRepository volumeRepository,
                                 StorageClient storageClient,
                                 QuotaService quotaService,
                                 PrivateMangaAccess access,
                                 PrivateMangaMapper mapper) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.quotaService = quotaService;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateMangaResponse handle(UUID mangaId, VolumeFinalizeRequest request,
                                       UUID actorId, BigDecimal quotaGb) {
        Manga manga = access.findOwned(mangaId, actorId);

        var metadata = storageClient.getFileMetadata(request.objectName());

        quotaService.assertCanFit(actorId, quotaGb, metadata.size());

        Volume volume = manga.addVolume(
                VolumeNumber.of(request.volumeNumber()), request.objectName(),
                FileHash.of(metadata.md5()), metadata.size(), actorId);
        volumeRepository.save(volume);

        return mapper.toResponse(manga);
    }
}
