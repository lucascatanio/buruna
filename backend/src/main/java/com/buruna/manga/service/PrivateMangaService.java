package com.buruna.manga.service;

import com.buruna.manga.application.PrivateMangaAccess;
import com.buruna.manga.application.PrivateMangaMapper;
import com.buruna.manga.application.SlugAllocator;
import com.buruna.shared.exception.LegacyHttpDomainException;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import com.buruna.manga.domain.*;
import com.buruna.manga.dto.*;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

/**
 * Fluxo de submissão/moderação e promoção da coleção privada. O CRUD de coleção privada
 * (criar/editar/apagar mangá, upload-url, finalize, deletar volume, cota) foi extraído em
 * use cases dedicados no [4.4]; submit/approve/reject/promote permanecem aqui até serem
 * migrados para use cases próprios no [4.5]. Reutiliza os componentes compartilhados
 * (ownership, mapper único, slug) introduzidos no [4.4].
 */
@Service
public class PrivateMangaService {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final MangaRepository mangaRepository;
    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PrivateMangaAccess access;
    private final SlugAllocator slugAllocator;
    private final PrivateMangaMapper mapper;

    public PrivateMangaService(MangaRepository mangaRepository,
                               VolumeRepository volumeRepository,
                               StorageClient storageClient,
                               EmailService emailService,
                               UserRepository userRepository,
                               PrivateMangaAccess access,
                               SlugAllocator slugAllocator,
                               PrivateMangaMapper mapper) {
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.access = access;
        this.slugAllocator = slugAllocator;
        this.mapper = mapper;
    }

    @Transactional
    public PrivateMangaResponse submitForApproval(UUID id, User owner) {
        Manga manga = access.findOwned(id, owner.getId());

        manga.submitForApproval();
        mangaRepository.save(manga);

        userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE).forEach(admin ->
                emailService.sendMangaSubmissionNotification(
                        admin.getEmail(), owner.getUsername(), manga.getTitle()));

        return mapper.toResponse(manga);
    }

    @Transactional(readOnly = true)
    public Page<PendingSubmissionResponse> listPendingSubmissions(Pageable pageable) {
        Page<Manga> page = mangaRepository.findBySubmissionStatus(MangaSubmissionStatus.PENDING, pageable);

        List<UUID> ownerIds = page.map(Manga::getOwnerId).toList();
        Map<UUID, User> owners = new HashMap<>();
        userRepository.findAllById(ownerIds).forEach(u -> owners.put(u.getId(), u));

        return page.map(m -> {
            String coverUrl = m.getCoverUrl() != null
                    ? storageClient.generateSignedUrl(m.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                    : null;
            User owner = owners.get(m.getOwnerId());
            return new PendingSubmissionResponse(
                    m.getId(), m.getTitle(), coverUrl,
                    owner != null ? owner.getUsername() : null,
                    owner != null ? owner.getEmail() : null,
                    m.getSubmittedAt());
        });
    }

    @Transactional
    public void approveSubmission(UUID id, User admin) {
        Manga manga = mangaRepository.findById(id)
                .orElseThrow(() -> new MangaNotFoundException(id));

        manga.approve(admin.getId());
        mangaRepository.save(manga);

        ownerEmail(manga).ifPresent(email ->
                emailService.sendMangaApprovalNotification(email, manga.getTitle()));
    }

    @Transactional
    public void rejectSubmission(UUID id, User admin, String reason) {
        Manga manga = mangaRepository.findById(id)
                .orElseThrow(() -> new MangaNotFoundException(id));

        manga.reject(admin.getId(), reason);
        mangaRepository.save(manga);

        ownerEmail(manga).ifPresent(email ->
                emailService.sendMangaRejectionNotification(email, manga.getTitle(), reason));
    }

    @Transactional
    public PrivateMangaResponse promote(UUID id, User owner) {
        if (owner.getRole() != Role.COLLABORATOR && owner.getRole() != Role.ADMIN) {
            throw new LegacyHttpDomainException(HttpStatus.FORBIDDEN,
                    "Apenas colaboradores e administradores podem promover mangás para a biblioteca pública");
        }

        Manga manga = access.findOwned(id, owner.getId());

        // 1. título duplicado na biblioteca pública
        if (mangaRepository.existsByTitleIgnoreCaseAndIsPublicTrue(manga.getTitle())) {
            throw new LegacyHttpDomainException(HttpStatus.CONFLICT,
                    "Já existe um mangá com este título na biblioteca pública");
        }

        // 2. hash de volume duplicado em mangá público
        List<Volume> volumes = volumeRepository.findByMangaId(id);
        boolean hasPublicHash = volumes.stream()
                .anyMatch(v -> volumeRepository.existsByFileHashAndMangaIsPublicTrue(v.getFileHash()));
        if (hasPublicHash) {
            throw new LegacyHttpDomainException(HttpStatus.CONFLICT,
                    "Um ou mais volumes já existem na biblioteca pública");
        }

        // 3. slug em conflito: regenera se necessário
        if (mangaRepository.existsBySlug(manga.getSlug())) {
            manga.changeSlug(slugAllocator.allocate(manga.getTitle()));
        }

        manga.promoteToPublic();
        return mapper.toResponse(mangaRepository.save(manga));
    }

    private Optional<String> ownerEmail(Manga manga) {
        return userRepository.findById(manga.getOwnerId()).map(User::getEmail);
    }
}
