package com.buruna.work.application;

import com.buruna.work.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Porta pública do contexto work para o contexto reading:
 * retorna metadados de volumes (número e work pai) por ids,
 * sem expor entidades internas (ADR-35).
 */
@Service
public class GetVolumeInfoUseCase {

    private final VolumeRepository volumeRepository;

    public GetVolumeInfoUseCase(VolumeRepository volumeRepository) {
        this.volumeRepository = volumeRepository;
    }

    @Transactional(readOnly = true)
    public Map<UUID, VolumeInfo> getInfoByIds(Collection<UUID> volumeIds) {
        if (volumeIds.isEmpty()) return Map.of();
        return volumeRepository.findAllById(volumeIds).stream()
                .collect(Collectors.toMap(
                        v -> v.getId(),
                        v -> new VolumeInfo(v.getId(), v.getVolumeNumber(), v.getWork().getId())
                ));
    }
}
