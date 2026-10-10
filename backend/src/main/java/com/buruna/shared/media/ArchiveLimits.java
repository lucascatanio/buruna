package com.buruna.shared.media;

/** Limites de extração, para se defender de arquivos malformados ou zip bombs. */
public record ArchiveLimits(int maxPages, long maxTotalBytes, long maxPageBytes) {

    public ArchiveLimits {
        if (maxPages <= 0 || maxTotalBytes <= 0 || maxPageBytes <= 0) {
            throw new IllegalArgumentException("Todos os limites devem ser maiores que zero");
        }
    }
}
