package com.buruna.engagement.application;

import com.buruna.engagement.domain.ReadingList;
import com.buruna.engagement.domain.ReadingListItemNotFoundException;
import com.buruna.engagement.persistence.ReadingListRepository;


import com.buruna.work.application.FindPublicWorkUseCase;
import com.buruna.work.application.GetWorkInfoUseCase;
import com.buruna.work.application.WorkInfo;
import com.buruna.shared.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReadingListService {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final ReadingListRepository readingListRepository;
    private final FindPublicWorkUseCase findPublicWorkUseCase;
    private final GetWorkInfoUseCase getWorkInfoUseCase;
    private final StorageClient storageClient;

    public ReadingListService(ReadingListRepository readingListRepository,
                              FindPublicWorkUseCase findPublicWorkUseCase,
                              GetWorkInfoUseCase getWorkInfoUseCase,
                              StorageClient storageClient) {
        this.readingListRepository = readingListRepository;
        this.findPublicWorkUseCase = findPublicWorkUseCase;
        this.getWorkInfoUseCase = getWorkInfoUseCase;
        this.storageClient = storageClient;
    }

    @Transactional(readOnly = true)
    public List<ReadingListResponse> findAll(UUID actorId) {
        List<ReadingList> entries = readingListRepository.findAllByUserIdOrderByUpdatedAtDesc(actorId);
        if (entries.isEmpty()) return List.of();

        Set<UUID> workIds = entries.stream().map(ReadingList::getWorkId).collect(Collectors.toSet());
        Map<UUID, WorkInfo> infoMap = getWorkInfoUseCase.getInfoByIds(workIds);

        return entries.stream()
                .map(rl -> toResponse(rl, infoMap.get(rl.getWorkId())))
                .toList();
    }

    @Transactional
    public ReadingListResponse upsert(UUID workId, ReadingListRequest request, UUID actorId) {
        WorkInfo info = findPublicWorkUseCase.getPublicWorkInfo(workId);

        ReadingList entry = readingListRepository
                .findByUserIdAndWorkId(actorId, workId)
                .orElseGet(() -> ReadingList.create(actorId, workId, request.status()));

        if (entry.getId() != null) {
            entry.updateStatus(request.status());
        }

        ReadingList saved = readingListRepository.save(entry);
        return toResponse(saved, info);
    }

    @Transactional
    public void remove(UUID workId, UUID actorId) {
        if (!readingListRepository.existsByUserIdAndWorkId(actorId, workId)) {
            throw new ReadingListItemNotFoundException(workId);
        }
        readingListRepository.deleteByUserIdAndWorkId(actorId, workId);
    }

    private ReadingListResponse toResponse(ReadingList rl, WorkInfo info) {
        String coverUrl = info.coverUrl() != null
                ? storageClient.generateSignedUrl(info.coverUrl(), COVER_URL_EXPIRATION).toString()
                : null;
        return new ReadingListResponse(
                info.id(),
                info.slug(),
                info.title(),
                coverUrl,
                rl.getStatus(),
                rl.getUpdatedAt()
        );
    }
}
