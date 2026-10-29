package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.PublicTitleConflictException;
import com.buruna.work.domain.PublicVolumeConflictException;
import com.buruna.work.domain.Volume;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.work.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * COLLABORATOR/ADMIN promove o próprio mangá privado direto para o catálogo público. RBAC
 * fica na borda (@PreAuthorize); posse por {@code actorId} (ADR-35). Preserva a validação de
 * conflito (título/hash) contra a biblioteca pública, agora com exceções de domínio puras
 * (ADR-33) em vez de HttpStatus na application. O slug não é revalidado: ele já é único na
 * tabela inteira (privados e públicos), então o mangá promovido nunca conflita com outro.
 */
@Service
public class PromoteWorkUseCase {

    private final WorkRepository workRepository;
    private final VolumeRepository volumeRepository;
    private final PrivateWorkAccess access;
    private final PrivateWorkMapper mapper;

    public PromoteWorkUseCase(WorkRepository workRepository,
                               VolumeRepository volumeRepository,
                               PrivateWorkAccess access,
                               PrivateWorkMapper mapper) {
        this.workRepository = workRepository;
        this.volumeRepository = volumeRepository;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateWorkResponse handle(UUID workId, UUID actorId) {
        Work work = access.findOwned(workId, actorId);

        // 1. título duplicado na biblioteca pública
        if (workRepository.existsByTitleIgnoreCaseAndIsPublicTrue(work.getTitle())) {
            throw new PublicTitleConflictException(work.getTitle());
        }

        // 2. hash de volume duplicado em mangá público
        List<Volume> volumes = volumeRepository.findByWorkId(workId);
        boolean hasPublicHash = volumes.stream()
                .anyMatch(v -> volumeRepository.existsByFileHashAndWorkIsPublicTrue(v.getFileHash()));
        if (hasPublicHash) {
            throw new PublicVolumeConflictException();
        }

        work.promoteToPublic();
        return mapper.toResponse(workRepository.save(work));
    }
}
