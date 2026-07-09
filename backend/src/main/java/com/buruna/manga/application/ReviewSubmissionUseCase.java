package com.buruna.manga.application;

import com.buruna.identity.application.GetUserSummaryUseCase;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.persistence.MangaRepository;
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

    private final MangaRepository mangaRepository;
    private final EmailService emailService;
    private final GetUserSummaryUseCase getUserSummary;

    public ReviewSubmissionUseCase(MangaRepository mangaRepository,
                                   EmailService emailService,
                                   GetUserSummaryUseCase getUserSummary) {
        this.mangaRepository = mangaRepository;
        this.emailService = emailService;
        this.getUserSummary = getUserSummary;
    }

    @Transactional
    public void approve(UUID mangaId, UUID reviewerId) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));

        manga.approve(reviewerId);
        mangaRepository.save(manga);

        getUserSummary.findById(manga.getOwnerId()).ifPresent(owner ->
                emailService.sendMangaApprovalNotification(owner.email(), manga.getTitle()));
    }

    @Transactional
    public void reject(UUID mangaId, UUID reviewerId, String reason) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));

        manga.reject(reviewerId, reason);
        mangaRepository.save(manga);

        getUserSummary.findById(manga.getOwnerId()).ifPresent(owner ->
                emailService.sendMangaRejectionNotification(owner.email(), manga.getTitle(), reason));
    }
}
