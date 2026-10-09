package com.buruna.work.application;

import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkNotFoundException;
import com.buruna.work.persistence.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Lista os volumes de um mangá público (404 se o mangá não for público). */
@Service
public class ListPublicVolumesUseCase {

    private final WorkRepository workRepository;
    private final VolumeResponseMapper volumeResponseMapper;

    public ListPublicVolumesUseCase(WorkRepository workRepository,
                                    VolumeResponseMapper volumeResponseMapper) {
        this.workRepository = workRepository;
        this.volumeResponseMapper = volumeResponseMapper;
    }

    @Transactional(readOnly = true)
    public List<VolumeResponse> handle(UUID workId) {
        Work work = workRepository.findById(workId)
                .filter(Work::isPublic)
                .orElseThrow(() -> new WorkNotFoundException(workId));
        return volumeResponseMapper.toResponseList(work.getVolumes());
    }
}
