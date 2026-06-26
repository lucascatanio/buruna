package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Mapper único do agregado privado {@link Manga} → {@link PrivateMangaResponse} (ADR-34).
 * Substitui a montagem de {@link VolumeResponse} antes duplicada em PrivateMangaService.
 * Lê os volumes diretamente do agregado ({@link Manga#getVolumes()}).
 */
@Component
public class PrivateMangaMapper {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final StorageClient storageClient;

    public PrivateMangaMapper(StorageClient storageClient) {
        this.storageClient = storageClient;
    }

    public PrivateMangaResponse toResponse(Manga manga) {
        String coverSignedUrl = manga.getCoverUrl() != null
                ? storageClient.generateSignedUrl(manga.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;

        List<VolumeResponse> volumes = manga.getVolumes().stream()
                .sorted(Comparator.comparingInt(Volume::getVolumeNumber))
                .map(v -> new VolumeResponse(
                        v.getId(), v.getVolumeNumber(),
                        v.getFileSizeBytes(), v.getCreatedAt()))
                .toList();

        String status = manga.getSubmissionStatus() != null
                ? manga.getSubmissionStatus().name() : null;

        return new PrivateMangaResponse(
                manga.getId(),
                manga.getTitle(),
                manga.getSynopsis(),
                coverSignedUrl,
                volumes,
                manga.getCreatedAt(),
                manga.getUpdatedAt(),
                status,
                manga.getRejectionReason()
        );
    }
}
