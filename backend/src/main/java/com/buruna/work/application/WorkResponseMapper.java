package com.buruna.work.application;

import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.Work;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper do agregado público {@link Work} → {@link WorkResponse} (catálogo). Delega a
 * montagem de {@link VolumeResponse} ao {@link VolumeResponseMapper} (fonte única, ADR-34).
 */
@Component
public class WorkResponseMapper {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final StorageClient storageClient;
    private final VolumeResponseMapper volumeResponseMapper;

    public WorkResponseMapper(StorageClient storageClient, VolumeResponseMapper volumeResponseMapper) {
        this.storageClient = storageClient;
        this.volumeResponseMapper = volumeResponseMapper;
    }

    public WorkResponse toResponse(Work work, boolean includeVolumes) {
        List<VolumeResponse> volumes = includeVolumes
                ? volumeResponseMapper.toResponseList(work.getVolumes())
                : List.of();

        Set<TagResponse> tags = work.getTags().stream()
                .map(t -> new TagResponse(
                        t.getId(), t.getName(), t.getSlug(),
                        new TagCategoryResponse(t.getCategory().getId(), t.getCategory().getName())))
                .collect(Collectors.toSet());

        String coverSignedUrl = work.getCoverUrl() != null
                ? storageClient.generateSignedUrl(work.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;

        return new WorkResponse(
                work.getId(),
                work.getSlug(),
                work.getTitle(),
                work.getAlternativeTitles(),
                work.getSynopsis(),
                coverSignedUrl,
                work.getFormat(),
                work.getOriginCountry(),
                work.getStatusOrigin(),
                work.getStatusSite(),
                work.getYear(),
                work.getContentWarnings(),
                work.getAvgRating(),
                work.getRatingCount(),
                work.getViewCount(),
                work.isPublic(),
                work.getOwnerId(),
                tags,
                volumes,
                work.getCreatedAt(),
                work.getUpdatedAt()
        );
    }
}
