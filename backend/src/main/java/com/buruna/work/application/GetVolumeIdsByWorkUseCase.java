package com.buruna.work.application;

import com.buruna.work.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Porta pública do contexto work para o contexto reading (ADR-35, ADR-39):
 * retorna os IDs dos volumes de um mangá ordenados por volume_number DESC.
 * A ordenação é responsabilidade do contexto work — ele é dono do agregado Volume.
 */
@Service
public class GetVolumeIdsByWorkUseCase {

    private final VolumeRepository volumeRepository;

    public GetVolumeIdsByWorkUseCase(VolumeRepository volumeRepository) {
        this.volumeRepository = volumeRepository;
    }

    @Transactional(readOnly = true)
    public List<UUID> getVolumeIdsOrderedByNumberDesc(UUID workId) {
        return volumeRepository.findIdsByWorkIdOrderByVolumeNumberDesc(workId);
    }
}
