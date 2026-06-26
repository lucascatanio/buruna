package com.buruna.manga.application;

import com.buruna.identity.application.GetUserSummaryUseCase;
import com.buruna.identity.application.UserSummary;
import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaSubmissionStatus;
import com.buruna.manga.dto.PendingSubmissionResponse;
import com.buruna.manga.repository.MangaRepository;
import com.buruna.shared.storage.StorageClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lista (admin-facing) as submissões pendentes, enriquecidas com nome/e-mail do dono. Os
 * dados do dono vêm de um use case público de identity (ADR-39), em batch por ownerId.
 */
@Service
public class ListPendingSubmissionsUseCase {

    private static final Duration COVER_URL_EXPIRATION = Duration.ofHours(1);

    private final MangaRepository mangaRepository;
    private final StorageClient storageClient;
    private final GetUserSummaryUseCase getUserSummary;

    public ListPendingSubmissionsUseCase(MangaRepository mangaRepository,
                                         StorageClient storageClient,
                                         GetUserSummaryUseCase getUserSummary) {
        this.mangaRepository = mangaRepository;
        this.storageClient = storageClient;
        this.getUserSummary = getUserSummary;
    }

    @Transactional(readOnly = true)
    public Page<PendingSubmissionResponse> handle(Pageable pageable) {
        Page<Manga> page = mangaRepository.findBySubmissionStatus(MangaSubmissionStatus.PENDING, pageable);

        Map<UUID, UserSummary> owners = getUserSummary.findAllById(page.map(Manga::getOwnerId).toList())
                .stream().collect(Collectors.toMap(UserSummary::id, Function.identity()));

        return page.map(m -> {
            String coverUrl = m.getCoverUrl() != null
                    ? storageClient.generateSignedUrl(m.getCoverUrl(), COVER_URL_EXPIRATION).toString()
                    : null;
            UserSummary owner = owners.get(m.getOwnerId());
            return new PendingSubmissionResponse(
                    m.getId(), m.getTitle(), coverUrl,
                    owner != null ? owner.username() : null,
                    owner != null ? owner.email() : null,
                    m.getSubmittedAt());
        });
    }
}
