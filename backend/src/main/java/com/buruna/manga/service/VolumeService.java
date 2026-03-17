package com.buruna.manga.service;

import com.buruna.infra.exception.DomainException;
import com.buruna.infra.storage.StorageClient;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.exception.DuplicateVolumeException;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.exception.VolumeNotFoundException;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.user.domain.Role;
import com.buruna.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class VolumeService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf"
    );

    private final MangaRepository mangaRepository;
    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;
    private final long maxFileSizeBytes;

    public VolumeService(MangaRepository mangaRepository,
                         VolumeRepository volumeRepository,
                         StorageClient storageClient,
                         @Value("${app.upload.max-file-size-mb:500}") long maxFileSizeMb) {
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
        this.maxFileSizeBytes = maxFileSizeMb * 1024L * 1024L;
    }

    @Transactional(readOnly = true)
    public List<VolumeResponse> findByMangaId(UUID mangaId) {
        Manga manga = mangaRepository.findById(mangaId)
                .filter(Manga::isPublic)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));

        return manga.getVolumes().stream()
                .map(v -> new VolumeResponse(
                        v.getId(), v.getVolumeNumber(),
                        v.getFileSizeBytes(), v.getCreatedAt()))
                .toList();
    }

    @Transactional
    public VolumeResponse upload(UUID mangaId, Integer volumeNumber, MultipartFile file, User uploader) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));

        assertCanModify(manga, uploader);

        if (!manga.isPublic()) {
            throw new DomainException(HttpStatus.FORBIDDEN,
                    "Use /my/mangas para fazer upload em mangás privados");
        }

        validateFile(file);

        if (volumeRepository.existsByMangaIdAndVolumeNumber(mangaId, volumeNumber)) {
            throw new DuplicateVolumeException(volumeNumber);
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao ler o arquivo enviado");
        }

        String fileHash = computeSha256(fileBytes);

        if (volumeRepository.existsByFileHashAndMangaIsPublicTrue(fileHash)) {
            throw new DuplicateVolumeException();
        }

        String extension = extractExtension(file.getOriginalFilename());
        String objectName = "volumes/" + UUID.randomUUID() + extension;

        storageClient.upload(
                new ByteArrayInputStream(fileBytes),
                objectName,
                file.getContentType(),
                fileBytes.length
        );

        Volume volume = new Volume();
        volume.setManga(manga);
        volume.setVolumeNumber(volumeNumber);
        volume.setFileUrl(objectName);
        volume.setFileHash(fileHash);
        volume.setFileSizeBytes(file.getSize());
        volume.setUploadedBy(uploader);

        Volume saved = volumeRepository.save(volume);
        return new VolumeResponse(
                saved.getId(), saved.getVolumeNumber(),
                saved.getFileSizeBytes(), saved.getCreatedAt());
    }

    @Transactional
    public void delete(UUID mangaId, UUID volumeId, User currentUser) {
        Volume volume = volumeRepository.findByIdAndMangaId(volumeId, mangaId)
                .orElseThrow(() -> new VolumeNotFoundException(volumeId));

        assertCanModify(volume.getManga(), currentUser);
        storageClient.delete(volume.getFileUrl());
        volumeRepository.delete(volume);
    }

    // helpers internos

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Arquivo não pode ser vazio");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new DomainException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato não suportado. Use PDF.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Arquivo excede o tamanho máximo permitido");
        }
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    private void assertCanModify(Manga manga, User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = manga.getOwner().getId().equals(user.getId());
        if (!isAdmin && !isOwner) {
            throw new DomainException(HttpStatus.FORBIDDEN,
                    "Você não tem permissão para modificar os volumes deste mangá");
        }
    }

}