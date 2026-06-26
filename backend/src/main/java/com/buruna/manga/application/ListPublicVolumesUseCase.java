package com.buruna.manga.application;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.dto.VolumeResponse;
import com.buruna.manga.exception.MangaNotFoundException;
import com.buruna.manga.repository.MangaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Lista os volumes de um mangá público (404 se o mangá não for público). */
@Service
public class ListPublicVolumesUseCase {

    private final MangaRepository mangaRepository;
    private final VolumeResponseMapper volumeResponseMapper;

    public ListPublicVolumesUseCase(MangaRepository mangaRepository,
                                    VolumeResponseMapper volumeResponseMapper) {
        this.mangaRepository = mangaRepository;
        this.volumeResponseMapper = volumeResponseMapper;
    }

    @Transactional(readOnly = true)
    public List<VolumeResponse> handle(UUID mangaId) {
        Manga manga = mangaRepository.findById(mangaId)
                .filter(Manga::isPublic)
                .orElseThrow(() -> new MangaNotFoundException(mangaId));
        return volumeResponseMapper.toResponseList(manga.getVolumes());
    }
}
