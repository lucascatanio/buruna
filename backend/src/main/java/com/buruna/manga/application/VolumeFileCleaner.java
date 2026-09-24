package com.buruna.manga.application;

import com.buruna.manga.domain.Volume;
import com.buruna.manga.persistence.VolumeRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Component;

/**
 * Ponto único que decide se o arquivo de um volume pode ser apagado do storage
 * (FIND-002). Antes da validação de {@link com.buruna.manga.domain.VolumeObjectName}
 * (ADR-40), o finalize aceitava qualquer {@code objectName} vindo do cliente — nada
 * impedia dois volumes (de mangás diferentes) de apontarem para o mesmo objeto físico
 * no bucket. Para dados já criados sob esse comportamento, apagar um dos volumes não
 * pode apagar o arquivo do outro: por isso todo ponto que apaga arquivo de VOLUME
 * (não capa, não avatar — esses nunca tiveram objectName vindo do cliente) passa por
 * aqui em vez de chamar {@code storageClient.delete} direto.
 */
@Component
public class VolumeFileCleaner {

    private final VolumeRepository volumeRepository;
    private final StorageClient storageClient;

    public VolumeFileCleaner(VolumeRepository volumeRepository, StorageClient storageClient) {
        this.volumeRepository = volumeRepository;
        this.storageClient = storageClient;
    }

    public void delete(Volume volume) {
        if (!isShared(volume)) {
            storageClient.delete(volume.getFileUrl());
        }
    }

    public boolean isShared(Volume volume) {
        return volumeRepository.existsByFileUrlAndIdNot(volume.getFileUrl(), volume.getId());
    }
}
