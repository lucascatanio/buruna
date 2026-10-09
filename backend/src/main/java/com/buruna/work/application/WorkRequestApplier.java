package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.Tag;
import com.buruna.work.persistence.TagRepository;
import com.buruna.shared.storage.StorageClient;
import com.buruna.shared.storage.StorageUploadHelper;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Aplica um {@link WorkRequest} ao agregado de catálogo: carrega as tags, atualiza os
 * metadados e troca a capa (apagando a anterior). Compartilhado por create e update para
 * não duplicar a montagem.
 */
@Component
public class WorkRequestApplier {

    private final TagRepository tagRepository;
    private final StorageClient storageClient;

    public WorkRequestApplier(TagRepository tagRepository, StorageClient storageClient) {
        this.tagRepository = tagRepository;
        this.storageClient = storageClient;
    }

    public void apply(Work work, WorkRequest request) {
        Set<Tag> tags = (request.tagIds() != null && !request.tagIds().isEmpty())
                ? new HashSet<>(tagRepository.findAllById(request.tagIds()))
                : new HashSet<>();

        work.updateCatalogDetails(
                request.title(),
                request.alternativeTitles(),
                request.synopsis(),
                request.format(),
                request.originCountry(),
                request.statusOrigin(),
                request.statusSite(),
                request.year(),
                request.contentWarnings(),
                tags);

        if (request.coverBase64() != null && !request.coverBase64().isBlank()) {
            work.changeCover(uploadCover(request.coverBase64(), work.getCoverUrl()));
        }
    }

    // faz upload da capa como objeto privado no GCS. aceita data URI ou base64 puro.
    private String uploadCover(String coverBase64, String existingCoverObjectName) {
        if (existingCoverObjectName != null) {
            storageClient.delete(existingCoverObjectName);
        }
        return StorageUploadHelper.uploadBase64Image(storageClient, coverBase64, "covers");
    }
}
