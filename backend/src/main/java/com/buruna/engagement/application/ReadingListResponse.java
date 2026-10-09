package com.buruna.engagement.application;

import com.buruna.engagement.domain.ReadingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingListResponse(
        UUID workId,
        String workSlug,
        String workTitle,
        String workCoverUrl,
        ReadingStatus status,
        OffsetDateTime updatedAt
) {
}
