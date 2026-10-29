package com.buruna.work.application;

import com.buruna.work.domain.Volume;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Fonte única da montagem de {@link VolumeResponse} (ADR-34), antes duplicada entre
 * WorkResponseMapper, VolumeService e PrivateWorkMapper. Ordena por número de volume.
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
