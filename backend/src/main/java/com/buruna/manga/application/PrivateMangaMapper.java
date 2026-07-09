package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Mapper único do agregado privado {@link Manga} → {@link PrivateMangaResponse} (ADR-34).
 * Delega a montagem de {@link VolumeResponse} ao {@link VolumeResponseMapper} (fonte única).
 * Lê os volumes diretamente do agregado ({@link Manga#getVolumes()}).
 */
@Component
public class PrivateMangaMapper {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final StorageClient storageClient;
    private final VolumeResponseMapper volumeResponseMapper;

    public PrivateMangaMapper(StorageClient storageClient, VolumeResponseMapper volumeResponseMapper) {
        this.storageClient = storageClient;
        this.volumeResponseMapper = volumeResponseMapper;
    }

    public PrivateMangaResponse toResponse(Manga manga) {
        String coverSignedUrl = manga.getCoverUrl() != null
                ? storageClient.generateSignedUrl(manga.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;

        List<VolumeResponse> volumes = volumeResponseMapper.toResponseList(manga.getVolumes());

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
