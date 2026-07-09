package com.buruna.manga.application.admin;

import com.buruna.manga.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agrega bytes usados por dono (mangás privados) para o dashboard de admin (ADR-39),
 * sem que o contexto admin toque {@code VolumeRepository} diretamente.
 */
@Service
public class GetStorageByOwnerUseCase {

    private final VolumeRepository volumeRepository;

    public GetStorageByOwnerUseCase(VolumeRepository volumeRepository) {
        this.volumeRepository = volumeRepository;
    }

    @Transactional(readOnly = true)
    public List<OwnerStorageUsage> handle() {
        return volumeRepository.findStorageByOwner().stream()
                .map(r -> new OwnerStorageUsage(
                        r.getOwnerId(),
                        r.getTotalBytes() != null ? r.getTotalBytes() : 0L))
                .toList();
    }
}
