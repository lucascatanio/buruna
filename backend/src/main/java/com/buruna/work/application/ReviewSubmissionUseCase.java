package com.buruna.work.application;

import com.buruna.identity.application.GetUserSummaryUseCase;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.shared.notification.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Moderação de submissões pelo ADMIN: aprova (publica) ou rejeita (com motivo) e notifica
 * o dono por e-mail. RBAC fica na borda (@PreAuthorize no controller); o {@code reviewerId}
 * chega como primitivo (ADR-35) e o e-mail do dono vem de um use case público de identity
 * (ADR-39).
 */
@Service
public class ReviewSubmissionUseCase {

    private final WorkRepository workRepository;
    private final EmailService emailService;
    private final GetUserSummaryUseCase getUserSummary;

    public ReviewSubmissionUseCase(WorkRepository workRepository,
                                   EmailService emailService,
                                   GetUserSummaryUseCase getUserSummary) {
        this.workRepository = workRepository;
        this.emailService = emailService;
        this.getUserSummary = getUserSummary;
    }

    @Transactional
    public void approve(UUID workId, UUID reviewerId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new WorkNotFoundException(workId));

        work.approve(reviewerId);
        workRepository.save(work);

        getUserSummary.findById(work.getOwnerId()).ifPresent(owner ->
                emailService.sendWorkApprovalNotification(owner.email(), work.getTitle()));
    }

    @Transactional
    public void reject(UUID workId, UUID reviewerId, String reason) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new WorkNotFoundException(workId));

        work.reject(reviewerId, reason);
        workRepository.save(work);

        getUserSummary.findById(work.getOwnerId()).ifPresent(owner ->
                emailService.sendWorkRejectionNotification(owner.email(), work.getTitle(), reason));
    }
}
