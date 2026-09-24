package com.buruna.manga.application.maintenance;

import com.buruna.manga.application.VolumeFileCleaner;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.persistence.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso público do contexto {@code manga}, consumido pelo job de inatividade
 * de {@code identity} (Epic 5.3) para apagar a coleção privada de um usuário desativado.
 *
 * <p>Recebe apenas o {@code userId} (UUID) — sem acoplamento reverso {@code manga → identity}.
 *
 * <p><b>Fronteira transacional (roadmap §7 [4.8], ADR-24):</b> a deleção das linhas de
 * banco (mangás privados do usuário + seus volumes, via cascade do agregado) ocorre
 * <b>dentro</b> desta transação. O I/O externo (GCS) fica <b>fora</b>: o método apenas
 * <b>coleta e retorna</b> os object names dos arquivos (capa dos mangás + arquivo dos
 * volumes); quem chama apaga no {@code StorageClient} <b>após o commit</b>, best-effort.
 * Falha de GCS não reverte o banco — órfãos são tolerados.
 *
 * <p>Escopo: apaga <b>somente</b> mangás {@code isPublic=false} do usuário; mangás
 * públicos permanecem intactos.
 *
 * <p>O arquivo de um volume só entra na lista devolvida se nenhum OUTRO volume ainda
 * o referenciar (ver {@link VolumeFileCleaner}) — checado aqui, dentro da
 * transação, antes do {@code deleteAll} remover as linhas. A capa não entra nessa
 * regra.
 */
@Service
public class DeletePrivateCollectionForUserUseCase {

    private final MangaRepository mangaRepository;
    private final VolumeFileCleaner volumeFileCleaner;

    public DeletePrivateCollectionForUserUseCase(MangaRepository mangaRepository,
                                                  VolumeFileCleaner volumeFileCleaner) {
        this.mangaRepository = mangaRepository;
        this.volumeFileCleaner = volumeFileCleaner;
    }

    /**
     * Apaga do banco a coleção privada do usuário e devolve os object names do GCS a
     * remover fora da transação. Nunca retorna {@code null}.
     */
    @Transactional
    public List<String> handle(UUID userId) {
        List<Manga> privateMangas = mangaRepository.findByOwnerIdAndIsPublicFalse(userId);

        List<String> objectNames = new ArrayList<>();
        for (Manga manga : privateMangas) {
            if (manga.getCoverUrl() != null) {
                objectNames.add(manga.getCoverUrl());
            }
            manga.getVolumes().forEach(volume -> {
                if (!volumeFileCleaner.isShared(volume)) {
                    objectNames.add(volume.getFileUrl());
                }
            });
        }

        mangaRepository.deleteAll(privateMangas);
        return objectNames;
    }
}
