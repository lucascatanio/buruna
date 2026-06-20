package com.buruna.manga.application;

import com.buruna.manga.repository.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Porta pública do contexto manga para o contexto reading (ADR-35, ADR-39):
 * retorna os IDs dos volumes de um mangá ordenados por volume_number DESC.
 * A ordenação é responsabilidade do contexto manga — ele é dono do agregado Volume.
 */
@Service
public class GetVolumeIdsByMangaUseCase {

    private final VolumeRepository volumeRepository;

    public GetVolumeIdsByMangaUseCase(VolumeRepository volumeRepository) {
        this.volumeRepository = volumeRepository;
    }

    @Transactional(readOnly = true)
    public List<UUID> getVolumeIdsOrderedByNumberDesc(UUID mangaId) {
        return volumeRepository.findIdsByMangaIdOrderByVolumeNumberDesc(mangaId);
    }
}
