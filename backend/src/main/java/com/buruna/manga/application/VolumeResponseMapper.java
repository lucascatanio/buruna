package com.buruna.manga.application;

import com.buruna.manga.domain.Volume;
import com.buruna.manga.dto.VolumeResponse;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Fonte única da montagem de {@link VolumeResponse} (ADR-34), antes duplicada entre
 * MangaResponseMapper, VolumeService e PrivateMangaMapper. Ordena por número de volume.
 */
@Component
public class VolumeResponseMapper {

    public VolumeResponse toResponse(Volume volume) {
        return new VolumeResponse(
                volume.getId(), volume.getVolumeNumber(),
                volume.getFileSizeBytes(), volume.getCreatedAt());
    }

    public List<VolumeResponse> toResponseList(Collection<Volume> volumes) {
        return volumes.stream()
                .sorted(Comparator.comparingInt(Volume::getVolumeNumber))
                .map(this::toResponse)
                .toList();
    }
}
