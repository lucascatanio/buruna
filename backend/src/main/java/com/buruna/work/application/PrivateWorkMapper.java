package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Mapper único do agregado privado {@link Work} → {@link PrivateWorkResponse} (ADR-34).
 * Delega a montagem de {@link VolumeResponse} ao {@link VolumeResponseMapper} (fonte única).
 * Lê os volumes diretamente do agregado ({@link Work#getVolumes()}).
 */
@Component
public class PrivateWorkMapper {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final StorageClient storageClient;
    private final VolumeResponseMapper volumeResponseMapper;

    public PrivateWorkMapper(StorageClient storageClient, VolumeResponseMapper volumeResponseMapper) {
        this.storageClient = storageClient;
        this.volumeResponseMapper = volumeResponseMapper;
    }

    public PrivateWorkResponse toResponse(Work work) {
        String coverSignedUrl = work.getCoverUrl() != null
                ? storageClient.generateSignedUrl(work.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;

        List<VolumeResponse> volumes = volumeResponseMapper.toResponseList(work.getVolumes());

        String status = work.getSubmissionStatus() != null
                ? work.getSubmissionStatus().name() : null;

        return new PrivateWorkResponse(
                work.getId(),
                work.getTitle(),
                work.getSynopsis(),
                coverSignedUrl,
                volumes,
                work.getCreatedAt(),
                work.getUpdatedAt(),
                status,
                work.getRejectionReason()
        );
    }
}
