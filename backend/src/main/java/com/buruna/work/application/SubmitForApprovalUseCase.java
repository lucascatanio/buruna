package com.buruna.work.application;

import com.buruna.identity.application.ListActiveAdminEmailsUseCase;
import com.buruna.work.domain.Work;
import com.buruna.work.persistence.WorkRepository;
import com.buruna.shared.notification.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * O dono submete um mangá privado para aprovação e notifica os administradores. Recebe
 * {@code actorId}/{@code actorUsername} como primitivos da borda (ADR-35); os e-mails dos
 * admins vêm de um use case público de identity (ADR-39), sem tocar o {@code UserRepository}.
 */
@Service
public class SubmitForApprovalUseCase {

    private final WorkRepository workRepository;
    private final PrivateWorkAccess access;
    private final PrivateWorkMapper mapper;
    private final EmailService emailService;
    private final ListActiveAdminEmailsUseCase listActiveAdminEmails;

    public SubmitForApprovalUseCase(WorkRepository workRepository,
                                    PrivateWorkAccess access,
                                    PrivateWorkMapper mapper,
                                    EmailService emailService,
                                    ListActiveAdminEmailsUseCase listActiveAdminEmails) {
        this.workRepository = workRepository;
        this.access = access;
        this.mapper = mapper;
        this.emailService = emailService;
        this.listActiveAdminEmails = listActiveAdminEmails;
    }

    @Transactional
    public PrivateWorkResponse handle(UUID workId, UUID actorId, String actorUsername) {
        Work work = access.findOwned(workId, actorId);

        work.submitForApproval();
        workRepository.save(work);

        emailService.sendWorkSubmissionNotification(
                listActiveAdminEmails.handle(), actorUsername, work.getTitle());

        return mapper.toResponse(work);
    }
}
