package com.buruna.manga.service;

import com.buruna.infra.exception.DomainException;
import com.buruna.infra.storage.StorageClient;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.domain.Tag;
import com.buruna.manga.dto.MangaRequest;
import com.buruna.manga.dto.MangaResponse;
import com.buruna.manga.dto.TagCategoryResponse;
import com.buruna.manga.dto.TagResponse;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.exception.MangaAlreadyExistsException;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.MangaSpecification;
import com.buruna.manga.repository.TagRepository;
import com.buruna.user.domain.Role;
import com.buruna.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MangaService {
    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final MangaRepository mangaRepository;
    private final TagRepository tagRepository;
    private final StorageClient storageClient;

    public MangaService(MangaRepository mangaRepository,
                        TagRepository tagRepository,
                        StorageClient storageClient) {
        this.mangaRepository = mangaRepository;
        this.tagRepository = tagRepository;
        this.storageClient = storageClient;
    }

    @Transactional
    public MangaResponse create(MangaRequest request, User owner) {
        if (mangaRepository.existsByTitleIgnoreCase(request.title())) {
            throw new MangaAlreadyExistsException(request.title());
        }

        Manga manga = new Manga();
        manga.setSlug(generateUniqueSlug(request.title()));
        manga.setOwner(owner);
        manga.setPublic(true);
        applyRequest(manga, request);

        return toResponse(mangaRepository.save(manga), true);
    }

    @Transactional(readOnly = true)
    public Page<MangaResponse> findPublic(
            String title,
            MangaFormat format,
            MangaStatusOrigin statusOrigin,
            Set<UUID> tagIds,
            Pageable pageable
    ) {
        Specification<Manga> spec = Specification
                .where(MangaSpecification.isPublic())
                .and(MangaSpecification.titleContains(title))
                .and(MangaSpecification.hasFormat(format))
                .and(MangaSpecification.hasStatusOrigin(statusOrigin))
                .and(MangaSpecification.hasTagIds(tagIds));

        return mangaRepository.findAll(spec, pageable).map(m -> toResponse(m, false));
    }

    @Transactional(readOnly = true)
    public MangaResponse findBySlug(String slug) {
        Manga manga = mangaRepository.findBySlug(slug)
                .filter(Manga::isPublic)
                .orElseThrow(() -> new MangaNotFoundException(slug));
        return toResponse(manga, true);
    }

    @Transactional
    public MangaResponse update(UUID id, MangaRequest request, User currentUser) {
        Manga manga = mangaRepository.findById(id)
                .orElseThrow(() -> new MangaNotFoundException(id));
        assertCanModify(manga, currentUser);
        applyRequest(manga, request);
        return toResponse(mangaRepository.save(manga), true);
    }

    @Transactional
    public void delete(UUID id, User currentUser) {
        Manga manga = mangaRepository.findById(id)
                .orElseThrow(() -> new MangaNotFoundException(id));
        assertCanModify(manga, currentUser);

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

    // helpers internos

    private void applyRequest(Manga manga, MangaRequest request) {
        manga.setTitle(request.title());
        manga.setAlternativeTitles(
                request.alternativeTitles() != null ? request.alternativeTitles() : List.of());
        manga.setSynopsis(request.synopsis());
        manga.setFormat(request.format());
        manga.setOriginCountry(request.originCountry());
        manga.setStatusOrigin(request.statusOrigin());
        manga.setStatusSite(request.statusSite());
        manga.setYear(request.year());
        manga.setContentWarnings(
                request.contentWarnings() != null ? request.contentWarnings() : List.of());

        if (request.coverBase64() != null && !request.coverBase64().isBlank()) {
            String objectName = uploadCover(request.coverBase64(), manga.getCoverUrl());
            manga.setCoverUrl(objectName);
        }

        Set<Tag> tags = (request.tagIds() != null && !request.tagIds().isEmpty())
                ? new HashSet<>(tagRepository.findAllById(request.tagIds()))
                : new HashSet<>();
        manga.setTags(tags);
    }

    // faz upload da capa como objeto privado no GCS. aceita data URI ou base64 puro.
    private String uploadCover(String coverBase64, String existingCoverObjectName) {
        if (existingCoverObjectName != null) {
            try {
                storageClient.delete(existingCoverObjectName);
            } catch (Exception ignored) {
            }
        }

        String base64Data;
        String contentType = "image/jpeg";

        if (coverBase64.startsWith("data:")) {
            int commaIndex = coverBase64.indexOf(',');
            String header = coverBase64.substring(5, commaIndex); // "image/jpeg;base64"
            contentType = header.split(";")[0];
            base64Data = coverBase64.substring(commaIndex + 1);
        } else {
            base64Data = coverBase64;
        }

        String extension = contentType.contains("/") ? contentType.split("/")[1] : "jpg";
        String objectName = "covers/" + UUID.randomUUID() + "." + extension;
        byte[] bytes = Base64.getDecoder().decode(base64Data);

        storageClient.upload(
                new ByteArrayInputStream(bytes),
                objectName,
                contentType,
                bytes.length
        );

        return objectName;
    }

    private String generateUniqueSlug(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String base = normalized.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");

        if (!mangaRepository.existsBySlug(base)) {
            return base;
        }

        int suffix = 2;
        while (mangaRepository.existsBySlug(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    private void assertCanModify(Manga manga, User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = manga.getOwner().getId().equals(user.getId());
        if (!isAdmin && !isOwner) {
            throw new DomainException(HttpStatus.FORBIDDEN,
                    "Você não tem permissão para modificar este mangá");
        }
    }

    MangaResponse toResponse(Manga manga, boolean includeVolumes) {
        List<VolumeResponse> volumes = includeVolumes
                ? manga.getVolumes().stream()
                .map(v -> new VolumeResponse(
                        v.getId(), v.getVolumeNumber(),
                        v.getFileSizeBytes(), v.getCreatedAt()))
                .toList()
                : List.of();

        Set<TagResponse> tags = manga.getTags().stream()
                .map(t -> new TagResponse(
                        t.getId(), t.getName(), t.getSlug(),
                        new TagCategoryResponse(t.getCategory().getId(), t.getCategory().getName())))
                .collect(Collectors.toSet());

        // gera signed URL para a capa se existir — operação local (crypto), sem chamada de rede
        String coverSignedUrl = manga.getCoverUrl() != null
                ? storageClient.generateSignedUrl(manga.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                : null;

        return new MangaResponse(
                manga.getId(),
                manga.getSlug(),
                manga.getTitle(),
                manga.getAlternativeTitles(),
                manga.getSynopsis(),
                coverSignedUrl,
                manga.getFormat(),
                manga.getOriginCountry(),
                manga.getStatusOrigin(),
                manga.getStatusSite(),
                manga.getYear(),
                manga.getContentWarnings(),
                manga.getAvgRating(),
                manga.getRatingCount(),
                manga.getViewCount(),
                manga.isPublic(),
                manga.getOwner().getId(),
                tags,
                volumes,
                manga.getCreatedAt(),
                manga.getUpdatedAt()
        );
    }
}