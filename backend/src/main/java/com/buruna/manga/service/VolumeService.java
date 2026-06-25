package com.buruna.manga.service;

import com.buruna.shared.exception.LegacyHttpDomainException;
import com.buruna.shared.storage.StorageClient;
import com.buruna.manga.domain.DuplicateVolumeException;
import com.buruna.manga.domain.FileHash;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.Volume;
import com.buruna.manga.domain.VolumeNumber;
import com.buruna.manga.dto.VolumeFinalizeRequest;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.dto.VolumeUploadUrlResponse;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class VolumeService {

    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(15);

    private final MangaRepository mangaRepository;
    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;

    public VolumeService(MangaRepository mangaRepository,
                         VolumeRepository volumeRepository,
                         StorageClient storageClient) {
        this.mangaRepository = mangaRepository;
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
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

    public VolumeUploadUrlResponse generateUploadUrl(UUID mangaId, Integer volumeNumber, User uploader) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));

        assertCanModify(manga, uploader);

        if (!manga.isPublic()) {
            throw new LegacyHttpDomainException(HttpStatus.FORBIDDEN,
                    "Use /my/mangas para fazer upload em mangás privados");
        }

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
    public VolumeResponse finalize(UUID mangaId, VolumeFinalizeRequest request, User uploader) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));

        assertCanModify(manga, uploader);

        if (!manga.isPublic()) {
            throw new LegacyHttpDomainException(HttpStatus.FORBIDDEN,
                    "Use /my/mangas para finalizar upload em mangás privados");
        }

        var metadata = storageClient.getFileMetadata(request.objectName());

        // dedup por hash atravessa agregados (outros mangás públicos): permanece na application
        if (volumeRepository.existsByFileHashAndMangaIsPublicTrue(metadata.md5())) {
            throw new DuplicateVolumeException();
        }

        // dedup por número é invariante do agregado (Manga.addVolume)
        Volume volume = manga.addVolume(
                VolumeNumber.of(request.volumeNumber()), request.objectName(),
                FileHash.of(metadata.md5()), metadata.size(), uploader.getId());

        Volume saved = volumeRepository.save(volume);
        return new VolumeResponse(
                saved.getId(), saved.getVolumeNumber(),
                saved.getFileSizeBytes(), saved.getCreatedAt());
    }

    @Transactional
    public void delete(UUID mangaId, UUID volumeId, User currentUser) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));

        assertCanModify(manga, currentUser);
        Volume volume = manga.removeVolume(volumeId);
        storageClient.delete(volume.getFileUrl());
        mangaRepository.save(manga);
    }

    // helpers internos

    private void assertCanModify(Manga manga, User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = manga.getOwnerId().equals(user.getId());
        if (!isAdmin && !isOwner) {
            throw new LegacyHttpDomainException(HttpStatus.FORBIDDEN,
                    "Você não tem permissão para modificar os volumes deste mangá");
        }
    }

}
