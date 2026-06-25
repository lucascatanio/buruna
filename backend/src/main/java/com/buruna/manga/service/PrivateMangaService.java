package com.buruna.manga.service;

import com.buruna.shared.exception.LegacyHttpDomainException;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import com.buruna.shared.storage.StorageUploadHelper;
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

@Service
public class PrivateMangaService {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);
    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(15);

    private final MangaRepository mangaRepository;
    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final StorageQuotaService quotaService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    public PrivateMangaService(MangaRepository mangaRepository,
                               VolumeRepository volumeRepository,
                               StorageClient storageClient,
                               StorageQuotaService quotaService,
                               EmailService emailService,
                               UserRepository userRepository) {
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.quotaService = quotaService;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PrivateMangaResponse findById(UUID id, User owner) {
        Manga manga = findPrivateByIdAndOwner(id, owner);
        List<Volume> volumes = volumeRepository.findByMangaId(id);
        return toResponse(manga, volumes);
    }

    @Transactional
    public PrivateMangaResponse createManga(String title,
                                            String synopsis,
                                            String coverBase64,
                                            User owner) {
        Manga manga = Manga.createPrivate(uniqueSlug(title), title, synopsis, owner.getId());

        if (coverBase64 != null && !coverBase64.isBlank()) {
            String coverObjectName = StorageUploadHelper.uploadBase64Image(
                    storageClient, coverBase64, "covers");
            manga.changeCover(coverObjectName);
        }

        Manga savedManga = mangaRepository.save(manga);
        return toResponse(savedManga, List.of());
    }

    public VolumeUploadUrlResponse generateUploadUrl(UUID mangaId, Integer volumeNumber, User owner) {
        findPrivateByIdAndOwner(mangaId, owner);

        if (volumeRepository.existsByMangaIdAndVolumeNumber(mangaId, volumeNumber)) {
            throw new DuplicateVolumeException(volumeNumber);
        }

        String objectName = "volumes/" + UUID.randomUUID() + ".pdf";
        var uploadUrl = storageClient.generateUploadSignedUrl(objectName, UPLOAD_URL_EXPIRATION);

        return new VolumeUploadUrlResponse(uploadUrl.toString(), objectName);
    }

    // RISCO: se finalize falhar após o upload GCS, o arquivo fica órfão no bucket.
    // Mitigação futura: lifecycle rule de 24h no GCS para objetos sem registro no banco.
    @Transactional
    public PrivateMangaResponse finalizeVolume(UUID mangaId, VolumeFinalizeRequest request, User owner) {
        Manga manga = findPrivateByIdAndOwner(mangaId, owner);

        var metadata = storageClient.getFileMetadata(request.objectName());

        quotaService.assertHasQuota(owner, metadata.size());

        Volume volume = manga.addVolume(
                VolumeNumber.of(request.volumeNumber()), request.objectName(),
                FileHash.of(metadata.md5()), metadata.size(), owner.getId());
        volumeRepository.save(volume);

        return toResponse(manga, manga.getVolumes());
    }

    @Transactional(readOnly = true)
    public Page<PrivateMangaResponse> findAllByOwner(User owner, Pageable pageable) {
        return mangaRepository.findAllByOwnerIdAndIsPublicFalse(owner.getId(), pageable)
                .map(m -> toResponse(m, m.getVolumes().stream().toList()));
    }

    @Transactional(readOnly = true)
    public QuotaInfo getQuotaInfo(User owner) {
        return quotaService.getQuotaInfo(owner);
    }

    @Transactional
    public PrivateMangaResponse update(UUID id, PrivateMangaRequest request, User owner) {
        Manga manga = findPrivateByIdAndOwner(id, owner);
        manga.updatePrivateDetails(request.title(), request.synopsis());
        return toResponse(mangaRepository.save(manga), manga.getVolumes().stream().toList());
    }

    @Transactional
    public void delete(UUID id, User owner) {
        Manga manga = findPrivateByIdAndOwner(id, owner);

        manga.getVolumes().forEach(v -> storageClient.delete(v.getFileUrl()));

        if (manga.getCoverUrl() != null) {
            storageClient.delete(manga.getCoverUrl());
        }

        mangaRepository.delete(manga);
    }

    @Transactional
    public PrivateMangaResponse deleteVolume(UUID mangaId, UUID volumeId, User owner) {
        Manga manga = findPrivateByIdAndOwner(mangaId, owner);
        Volume volume = manga.removeVolume(volumeId);
        storageClient.delete(volume.getFileUrl());
        mangaRepository.save(manga);
        return toResponse(manga, manga.getVolumes());
    }

    @Transactional
    public PrivateMangaResponse submitForApproval(UUID id, User owner) {
        Manga manga = findPrivateByIdAndOwner(id, owner);

        manga.submitForApproval();
        mangaRepository.save(manga);

        userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE).forEach(admin ->
                emailService.sendMangaSubmissionNotification(
                        admin.getEmail(), owner.getUsername(), manga.getTitle()));

        List<Volume> volumes = volumeRepository.findByMangaId(id);
        return toResponse(manga, volumes);
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

        Manga manga = findPrivateByIdAndOwner(id, owner);

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
            manga.changeSlug(uniqueSlug(manga.getTitle()));
        }

        manga.promoteToPublic();
        return toResponse(mangaRepository.save(manga), volumes);
    }

    // helpers internos

    private Manga findPrivateByIdAndOwner(UUID id, User owner) {
        Manga manga = mangaRepository.findById(id)
                .orElseThrow(() -> new MangaNotFoundException(id));
        if (manga.isPublic()) {
            throw new LegacyHttpDomainException(HttpStatus.NOT_FOUND, "Mangá não encontrado na coleção privada");
        }
        if (!manga.getOwnerId().equals(owner.getId())) {
            throw new LegacyHttpDomainException(HttpStatus.FORBIDDEN,
                    "Você não tem permissão para modificar este mangá");
        }
        return manga;
    }

    private Optional<String> ownerEmail(Manga manga) {
        return userRepository.findById(manga.getOwnerId()).map(User::getEmail);
    }

    private Slug uniqueSlug(String title) {
        Slug base = Slug.fromTitle(title);
        if (!mangaRepository.existsBySlug(base.value())) {
            return base;
        }
        int suffix = 2;
        while (mangaRepository.existsBySlug(base.withSuffix(suffix).value())) {
            suffix++;
        }
        return base.withSuffix(suffix);
    }

    private PrivateMangaResponse toResponse(Manga manga, List<Volume> volumes) {
        String coverSignedUrl = manga.getCoverUrl() != null
                ? storageClient.generateSignedUrl(manga.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;

        List<VolumeResponse> volumeResponses = volumes.stream()
                .sorted(Comparator.comparingInt(Volume::getVolumeNumber))
                .map(v -> new VolumeResponse(
                        v.getId(), v.getVolumeNumber(),
                        v.getFileSizeBytes(), v.getCreatedAt()))
                .toList();

        String status = manga.getSubmissionStatus() != null
                ? manga.getSubmissionStatus().name() : null;

        return new PrivateMangaResponse(
                manga.getId(),
                manga.getTitle(),
                manga.getSynopsis(),
                coverSignedUrl,
                volumeResponses,
                manga.getCreatedAt(),
                manga.getUpdatedAt(),
                status,
                manga.getRejectionReason()
        );
    }
}
