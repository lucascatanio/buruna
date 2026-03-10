package com.buruna.manga.dto;

import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.domain.MangaStatusSite;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MangaResponse(
        UUID id,
        String slug,
        String title,
        List<String> alternativeTitles,
        String synopsis,
        String coverUrl,
        MangaFormat format,
        String originCountry,
        MangaStatusOrigin statusOrigin,
        MangaStatusSite statusSite,
        Integer year,
        List<String> contentWarnings,
        BigDecimal avgRating,
        Integer ratingCount,
        Integer viewCount,
        boolean isPublic,
        UUID ownerId,
        Set<TagResponse> tags,
        // populado apenas no endpoint de detalhe (/mangas/{slug}) vazio na listagem.
        List<VolumeResponse> volumes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}