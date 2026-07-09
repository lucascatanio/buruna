package com.buruna.manga.application;

import com.buruna.shared.storage.StorageClient;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.MangaResponse;
import com.buruna.manga.dto.TagCategoryResponse;
import com.buruna.manga.dto.TagResponse;
import com.buruna.manga.dto.VolumeResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper do agregado público {@link Manga} → {@link MangaResponse} (catálogo). Delega a
 * montagem de {@link VolumeResponse} ao {@link VolumeResponseMapper} (fonte única, ADR-34).
 */
@Component
public class MangaResponseMapper {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final StorageClient storageClient;
    private final VolumeResponseMapper volumeResponseMapper;

    public MangaResponseMapper(StorageClient storageClient, VolumeResponseMapper volumeResponseMapper) {
        this.storageClient = storageClient;
        this.volumeResponseMapper = volumeResponseMapper;
    }

    public MangaResponse toResponse(Manga manga, boolean includeVolumes) {
        List<VolumeResponse> volumes = includeVolumes
                ? volumeResponseMapper.toResponseList(manga.getVolumes())
                : List.of();

        Set<TagResponse> tags = manga.getTags().stream()
                .map(t -> new TagResponse(
                        t.getId(), t.getName(), t.getSlug(),
                        new TagCategoryResponse(t.getCategory().getId(), t.getCategory().getName())))
                .collect(Collectors.toSet());

        String coverSignedUrl = manga.getCoverUrl() != null
                ? storageClient.generateSignedUrl(manga.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;

        return new MangaResponse(
                manga.getId(),
                manga.getSlug(),
                manga.getTitle(),
                manga.getAlternativeTitles(),
                manga.getSynopsis(),
                coverSignedUrl,
                manga.getFormat(),
                manga.getOriginCountry(),
                manga.getStatusOrigin(),
                manga.getStatusSite(),
                manga.getYear(),
                manga.getContentWarnings(),
                manga.getAvgRating(),
                manga.getRatingCount(),
                manga.getViewCount(),
                manga.isPublic(),
                manga.getOwnerId(),
                tags,
                volumes,
                manga.getCreatedAt(),
                manga.getUpdatedAt()
        );
    }
}
