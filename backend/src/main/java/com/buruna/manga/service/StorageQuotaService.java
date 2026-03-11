package com.buruna.manga.service;

import com.buruna.manga.dto.QuotaInfo;
import com.buruna.manga.exception.InsufficientStorageQuotaException;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StorageQuotaService {

    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    private final VolumeRepository volumeRepository;

    public StorageQuotaService(VolumeRepository volumeRepository) {
        this.volumeRepository = volumeRepository;
    }

    @Transactional(readOnly = true)
    public void assertHasQuota(User user, long additionalBytes) {
        long usedBytes = volumeRepository.sumPrivateFileSizeByOwnerId(user.getId());
        long quotaBytes = toBytes(user.getQuotaGb());
        if (usedBytes + additionalBytes > quotaBytes) {
            throw new InsufficientStorageQuotaException(user.getQuotaGb(), usedBytes, additionalBytes);
        }
    }

    @Transactional(readOnly = true)
    public QuotaInfo getQuotaInfo(User user) {
        long usedBytes = volumeRepository.sumPrivateFileSizeByOwnerId(user.getId());
        long quotaBytes = toBytes(user.getQuotaGb());
        return new QuotaInfo(usedBytes, quotaBytes);
    }

    private long toBytes(BigDecimal quotaGb) {
        return quotaGb.multiply(BigDecimal.valueOf(BYTES_PER_GB)).longValue();
    }
}