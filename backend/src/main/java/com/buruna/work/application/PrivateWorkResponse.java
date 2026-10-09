package com.buruna.work.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PrivateWorkResponse(
        UUID id,
        String title,
        String synopsis,
        String coverUrl,
        List<VolumeResponse> volumes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String submissionStatus,
        String rejectionReason
) {
}