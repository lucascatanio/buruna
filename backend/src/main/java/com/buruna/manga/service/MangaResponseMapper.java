package com.buruna.manga.service;

import com.buruna.infra.storage.StorageClient;
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

@Component
public class MangaResponseMapper {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);
    private final StorageClient storageClient;

    public MangaResponseMapper(StorageClient storageClient) {
        this.storageClient = storageClient;
    }

    public MangaResponse toResponse(Manga manga, boolean includeVolumes) {
        List<VolumeResponse> volumes = includeVolumes
                ? manga.getVolumes().stream()
                .map(v -> new VolumeResponse(
                        v.getId(), v.getVolumeNumber(),
                        v.getFileSizeBytes(), v.getCreatedAt()))
                .toList()
                : List.of();

        Set<TagResponse> tags = manga.getTags().stream()
                .map(t -> new TagResponse(
                        t.getId(), t.getName(), t.getSlug(),
                        new TagCategoryResponse(t.getCategory().getId(), t.getCategory().getName())))
                .collect(Collectors.toSet());

        // gera signed URL para a capa se existir operação local (crypto), sem chamada de rede
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
                manga.getOwner().getId(),
                tags,
                volumes,
                manga.getCreatedAt(),
                manga.getUpdatedAt()
        );
    }
}
