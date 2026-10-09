package com.buruna.work.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PendingSubmissionResponse(
        UUID id,
        String title,
        String coverUrl,
        String submitterUsername,
        String submitterEmail,
        OffsetDateTime submittedAt
) {
}
