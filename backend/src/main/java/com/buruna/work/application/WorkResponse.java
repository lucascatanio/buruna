package com.buruna.work.application;

import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.domain.WorkStatusSite;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record WorkResponse(
        UUID id,
        String slug,
        String title,
        List<String> alternativeTitles,
        String synopsis,
        String coverUrl,
        WorkFormat format,
        String originCountry,
        WorkStatusOrigin statusOrigin,
        WorkStatusSite statusSite,
        Integer year,
        List<String> contentWarnings,
        BigDecimal avgRating,
        Integer ratingCount,
        Integer viewCount,
        boolean isPublic,
        UUID ownerId,
        Set<TagResponse> tags,
        // populado apenas no endpoint de detalhe (/works/{slug}) vazio na listagem.
        List<VolumeResponse> volumes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}