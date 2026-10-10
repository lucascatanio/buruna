package com.buruna.work.application;

import com.buruna.work.domain.InsufficientStorageQuotaException;
import com.buruna.work.domain.Quota;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.VolumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Valida e informa a cota de armazenamento da coleção privada. O limite (em GB) vem do
 * usuário (contexto identity) como primitivo na borda (ADR-35); o consumo é somado das
 * tabelas do próprio contexto work. A aritmética fica no VO {@link Quota} (ADR-34 §4.3).
 */
@Service
public class QuotaService {

    private final VolumeRepository volumeRepository;
    private final ChapterRepository chapterRepository;
    private final WorkRepository workRepository;

    public QuotaService(VolumeRepository volumeRepository, ChapterRepository chapterRepository,
                        WorkRepository workRepository) {
        this.volumeRepository = volumeRepository;
        this.chapterRepository = chapterRepository;
        this.workRepository = workRepository;
    }

    /**
     * Trava os mangás privados do dono antes de somar o uso: sem isso, dois finalizes
     * concorrentes leem o mesmo uso, cada um cabe sozinho e os dois juntos estouram a cota.
     * O lock vale até o fim da transação de quem chama, que precisa gravar o volume nela.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanFit(UUID actorId, BigDecimal limitGb, long additionalBytes) {
        workRepository.lockPrivateByOwnerId(actorId);
        Quota quota = quotaFor(actorId, limitGb);
        if (!quota.canFit(additionalBytes)) {
            throw new InsufficientStorageQuotaException(limitGb, quota.usedBytes(), additionalBytes);
        }
    }

    @Transactional(readOnly = true)
    public QuotaInfo getQuotaInfo(UUID actorId, BigDecimal limitGb) {
        Quota quota = quotaFor(actorId, limitGb);
        return new QuotaInfo(quota.usedBytes(), quota.limitBytes());
    }

    private Quota quotaFor(UUID actorId, BigDecimal limitGb) {
        // conta o que está guardado: volumes, páginas de capítulos, arquivos de livro e arquivos
        // enviados que ainda não foram processados ou falharam com o arquivo mantido para nova tentativa
        long usedBytes = volumeRepository.sumPrivateFileSizeByOwnerId(actorId)
                + chapterRepository.sumPrivatePageBytesByOwnerId(actorId)
                + chapterRepository.sumPrivateSourceBytesByOwnerId(actorId)
                + chapterRepository.sumPrivateFileBytesByOwnerId(actorId);
        return Quota.of(limitGb, usedBytes);
    }
}
