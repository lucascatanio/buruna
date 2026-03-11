package com.buruna.manga.service;

import com.buruna.infra.exception.DomainException;
import com.buruna.infra.storage.StorageClient;
import com.buruna.infra.storage.StorageUploadHelper;
import com.buruna.manga.domain.*;
import com.buruna.manga.dto.PrivateMangaRequest;
import com.buruna.manga.dto.PrivateMangaResponse;
import com.buruna.manga.dto.QuotaInfo;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.exception.DuplicateVolumeException;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.user.domain.Role;
import com.buruna.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;

@Service
public class PrivateMangaService {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/epub+zip",
            "application/x-mobipocket-ebook"
    );

    private final MangaRepository mangaRepository;
    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final StorageQuotaService quotaService;
    private final long maxFileSizeBytes;

    public PrivateMangaService(MangaRepository mangaRepository,
                               VolumeRepository volumeRepository,
                               StorageClient storageClient,
                               StorageQuotaService quotaService,
                               @Value("${app.upload.max-file-size-mb:500}") long maxFileSizeMb) {
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.quotaService = quotaService;
        this.maxFileSizeBytes = maxFileSizeMb * 1024L * 1024L;
    }

    @Transactional
    public PrivateMangaResponse upload(String title,
                                       String synopsis,
                                       String coverBase64,
                                       Integer volumeNumber,
                                       MultipartFile file,
                                       User owner) {
        validateFile(file);
        quotaService.assertHasQuota(owner, file.getSize());

        String fileHash = computeSha256(file);

        if (volumeRepository.existsByFileHash(fileHash)) {
            throw new DuplicateVolumeException();
        }

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

        String extension = extractExtension(file.getOriginalFilename());
        String objectName = "volumes/" + UUID.randomUUID() + extension;

        try {
            storageClient.upload(file.getInputStream(), objectName, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao ler o arquivo enviado");
        }

        Volume volume = new Volume();
        volume.setManga(savedManga);
        volume.setVolumeNumber(volumeNumber);
        volume.setFileUrl(objectName);
        volume.setFileHash(fileHash);
        volume.setFileSizeBytes(file.getSize());
        volume.setUploadedBy(owner);
        volumeRepository.save(volume);

        return toResponse(savedManga, List.of(volume));
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
            try {
                storageClient.delete(manga.getCoverUrl());
            } catch (Exception ignored) {
                // falha ao deletar capa é não-crítica
            }
        }

        mangaRepository.delete(manga);
    }

    @Transactional
    public PrivateMangaResponse addVolume(UUID mangaId, Integer volumeNumber, MultipartFile file, User owner) {
        Manga manga = findPrivateByIdAndOwner(mangaId, owner);

        validateFile(file);
        quotaService.assertHasQuota(owner, file.getSize());

        if (volumeRepository.existsByMangaIdAndVolumeNumber(mangaId, volumeNumber)) {
            throw new DuplicateVolumeException(volumeNumber);
        }

        String fileHash = computeSha256(file);
        if (volumeRepository.existsByFileHash(fileHash)) {
            throw new DuplicateVolumeException();
        }

        String extension = extractExtension(file.getOriginalFilename());
        String objectName = "volumes/" + UUID.randomUUID() + extension;

        try {
            storageClient.upload(file.getInputStream(), objectName, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao ler o arquivo enviado");
        }

        Volume volume = new Volume();
        volume.setManga(manga);
        volume.setVolumeNumber(volumeNumber);
        volume.setFileUrl(objectName);
        volume.setFileHash(fileHash);
        volume.setFileSizeBytes(file.getSize());
        volume.setUploadedBy(owner);
        volumeRepository.save(volume);

        List<Volume> allVolumes = volumeRepository.findByMangaId(mangaId);
        return toResponse(manga, allVolumes);
    }

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
        manga.setPublic(true);
        return toResponse(mangaRepository.save(manga), manga.getVolumes().stream().toList());
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

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Arquivo não pode ser vazio");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new DomainException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato não suportado. Use PDF, EPUB ou MOBI");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Arquivo excede o tamanho máximo permitido");
        }
    }

    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        } catch (IOException e) {
            throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao ler o arquivo enviado");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
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
