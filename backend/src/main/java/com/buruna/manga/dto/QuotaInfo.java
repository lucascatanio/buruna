package com.buruna.manga.dto;

public record QuotaInfo(
        long usedBytes,
        long quotaBytes
) {
    public double usedGb() {
        return usedBytes / (1024.0 * 1024.0 * 1024.0);
    }

    public double quotaGb() {
        return quotaBytes / (1024.0 * 1024.0 * 1024.0);
    }

    public double usedPercent() {
        if (quotaBytes == 0) return 100.0;
        return (usedBytes * 100.0) / quotaBytes;
    }
}