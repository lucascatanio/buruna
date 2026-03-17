package com.buruna.manga.service;

import com.buruna.infra.exception.DomainException;
import com.buruna.infra.storage.StorageClient;
import com.buruna.infra.storage.StorageUploadHelper;
import com.buruna.manga.domain.*;
import com.buruna.manga.dto.*;
import com.buruna.manga.exception.DuplicateVolumeException;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.user.domain.Role;
import com.buruna.user.domain.User;
import com.google.cloud.storage.Blob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
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

    public PrivateMangaService(MangaRepository mangaRepository,
                               VolumeRepository volumeRepository,
                               StorageClient storageClient,
                               StorageQuotaService quotaService) {
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.quotaService = quotaService;
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
        Manga manga = new Manga();
        manga.setTitle(title);
        manga.setSynopsis(synopsis);
        manga.setSlug(generateUniqueSlug(title));
        manga.setOwner(owner);
        manga.setPublic(false);
        manga.setFormat(MangaFormat.MANGA);
        manga.setStatusOrigin(MangaStatusOrigin.ONGOING);
        manga.setStatusSite(MangaStatusSite.INCOMPLETE);
        manga.setAlternativeTitles(List.of());
        manga.setContentWarnings(List.of());
        manga.setTags(new HashSet<>());

        if (coverBase64 != null && !coverBase64.isBlank()) {
            String coverObjectName = StorageUploadHelper.uploadBase64Image(
                    storageClient, coverBase64, "covers");
            manga.setCoverUrl(coverObjectName);
        }

        Manga savedManga = mangaRepository.save(manga);
        return toResponse(savedManga, List.of());
    }

    public VolumeUploadUrlResponse generateUploadUrl(UUID mangaId, Integer volumeNumber, User owner) {
        Manga manga = findPrivateByIdAndOwner(mangaId, owner);

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

        Blob blob = storageClient.getBlob(request.objectName());
        String fileHash = blob.getMd5();
        long fileSizeBytes = blob.getSize();

        quotaService.assertHasQuota(owner, fileSizeBytes);

        if (volumeRepository.existsByMangaIdAndVolumeNumber(mangaId, request.volumeNumber())) {
            throw new DuplicateVolumeException(request.volumeNumber());
        }

        Volume volume = new Volume();
        volume.setManga(manga);
        volume.setVolumeNumber(request.volumeNumber());
        volume.setFileUrl(request.objectName());
        volume.setFileHash(fileHash);
        volume.setFileSizeBytes(fileSizeBytes);
        volume.setUploadedBy(owner);
        volumeRepository.save(volume);

        List<Volume> allVolumes = volumeRepository.findByMangaId(mangaId);
        return toResponse(manga, allVolumes);
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
        manga.setTitle(request.title());
        manga.setSynopsis(request.synopsis());
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

    // addVolume agora é feito via generateUploadUrl + finalizeVolume

    @Transactional
    public PrivateMangaResponse deleteVolume(UUID mangaId, UUID volumeId, User owner) {
        Manga manga = findPrivateByIdAndOwner(mangaId, owner);
        Volume volume = volumeRepository.findByIdAndMangaId(volumeId, mangaId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Volume não encontrado"));
        storageClient.delete(volume.getFileUrl());
        volumeRepository.delete(volume);
        List<Volume> remaining = volumeRepository.findByMangaId(mangaId);
        return toResponse(manga, remaining);
    }

    @Transactional
    public PrivateMangaResponse promote(UUID id, User owner) {
        if (owner.getRole() != Role.COLLABORATOR && owner.getRole() != Role.ADMIN) {
            throw new DomainException(HttpStatus.FORBIDDEN,
                    "Apenas colaboradores e administradores podem promover mangás para a biblioteca pública");
        }

        Manga manga = findPrivateByIdAndOwner(id, owner);

        // 1. título duplicado na biblioteca pública
        if (mangaRepository.existsByTitleIgnoreCaseAndIsPublicTrue(manga.getTitle())) {
            throw new DomainException(HttpStatus.CONFLICT,
                    "Já existe um mangá com este título na biblioteca pública");
        }

        // 2. hash de volume duplicado em mangá público
        List<Volume> volumes = volumeRepository.findByMangaId(id);
        boolean hasPublicHash = volumes.stream()
                .anyMatch(v -> volumeRepository.existsByFileHashAndMangaIsPublicTrue(v.getFileHash()));
        if (hasPublicHash) {
            throw new DomainException(HttpStatus.CONFLICT,
                    "Um ou mais volumes já existem na biblioteca pública");
        }

        // 3. slug em conflito: regenera se necessário
        if (mangaRepository.existsBySlug(manga.getSlug())) {
            manga.setSlug(generateUniqueSlug(manga.getTitle()));
        }

        manga.setPublic(true);
        return toResponse(mangaRepository.save(manga), volumes);
    }

    // helpers internos

    private Manga findPrivateByIdAndOwner(UUID id, User owner) {
        Manga manga = mangaRepository.findById(id)
                .orElseThrow(() -> new MangaNotFoundException(id));
        if (manga.isPublic()) {
            throw new DomainException(HttpStatus.NOT_FOUND, "Mangá não encontrado na coleção privada");
        }
        if (!manga.getOwner().getId().equals(owner.getId())) {
            throw new DomainException(HttpStatus.FORBIDDEN,
                    "Você não tem permissão para modificar este mangá");
        }
        return manga;
    }

    private String generateUniqueSlug(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String base = normalized.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");

        if (!mangaRepository.existsBySlug(base)) return base;

        int suffix = 2;
        while (mangaRepository.existsBySlug(base + "-" + suffix)) suffix++;
        return base + "-" + suffix;
    }

    private PrivateMangaResponse toResponse(Manga manga, List<Volume> volumes) {
        String coverSignedUrl = manga.getCoverUrl() != null
                ? storageClient.generateSignedUrl(manga.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;

        List<VolumeResponse> volumeResponses = volumes.stream()
                .sorted((a, b) -> Integer.compare(a.getVolumeNumber(), b.getVolumeNumber()))
                .map(v -> new VolumeResponse(
                        v.getId(), v.getVolumeNumber(),
                        v.getFileSizeBytes(), v.getCreatedAt()))
                .toList();

        return new PrivateMangaResponse(
                manga.getId(),
                manga.getTitle(),
                manga.getSynopsis(),
                coverSignedUrl,
                volumeResponses,
                manga.getCreatedAt(),
                manga.getUpdatedAt()
        );
    }
}
