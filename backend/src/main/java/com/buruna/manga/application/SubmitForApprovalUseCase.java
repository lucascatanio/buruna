package com.buruna.manga.application;

import com.buruna.identity.application.ListActiveAdminEmailsUseCase;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.persistence.MangaRepository;
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

    private final MangaRepository mangaRepository;
    private final PrivateMangaAccess access;
    private final PrivateMangaMapper mapper;
    private final EmailService emailService;
    private final ListActiveAdminEmailsUseCase listActiveAdminEmails;

    public SubmitForApprovalUseCase(MangaRepository mangaRepository,
                                    PrivateMangaAccess access,
                                    PrivateMangaMapper mapper,
                                    EmailService emailService,
                                    ListActiveAdminEmailsUseCase listActiveAdminEmails) {
        this.mangaRepository = mangaRepository;
        this.access = access;
        this.mapper = mapper;
        this.emailService = emailService;
        this.listActiveAdminEmails = listActiveAdminEmails;
    }

    @Transactional
    public PrivateMangaResponse handle(UUID mangaId, UUID actorId, String actorUsername) {
        Manga manga = access.findOwned(mangaId, actorId);

        manga.submitForApproval();
        mangaRepository.save(manga);

        listActiveAdminEmails.handle().forEach(adminEmail ->
                emailService.sendMangaSubmissionNotification(adminEmail, actorUsername, manga.getTitle()));

        return mapper.toResponse(manga);
    }
}
