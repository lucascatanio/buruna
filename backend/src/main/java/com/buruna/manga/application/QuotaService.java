package com.buruna.manga.application;

import com.buruna.manga.domain.InsufficientStorageQuotaException;
import com.buruna.manga.domain.Quota;
import com.buruna.manga.dto.QuotaInfo;
import com.buruna.manga.repository.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Valida e informa a cota de armazenamento da coleção privada. O limite (em GB) vem do
 * usuário (contexto identity) como primitivo na borda (ADR-35); o consumo é somado das
 * tabelas do próprio contexto manga. A aritmética fica no VO {@link Quota} (ADR-34 §4.3).
 */
@Service
public class QuotaService {

    private final VolumeRepository volumeRepository;

    public QuotaService(VolumeRepository volumeRepository) {
        this.volumeRepository = volumeRepository;
    }

    @Transactional(readOnly = true)
    public void assertCanFit(UUID actorId, BigDecimal limitGb, long additionalBytes) {
        Quota quota = quotaFor(actorId, limitGb);
        if (!quota.canFit(additionalBytes)) {
            throw new InsufficientStorageQuotaException(limitGb, quota.usedBytes(), additionalBytes);
        }
    }

    @Transactional(readOnly = true)
    public QuotaInfo getQuotaInfo(UUID actorId, BigDecimal limitGb) {
        Quota quota = quotaFor(actorId, limitGb);
        return new QuotaInfo(quota.usedBytes(), quota.limitBytes());
    }

    private Quota quotaFor(UUID actorId, BigDecimal limitGb) {
        long usedBytes = volumeRepository.sumPrivateFileSizeByOwnerId(actorId);
        return Quota.of(limitGb, usedBytes);
    }
}
