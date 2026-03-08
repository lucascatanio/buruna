package com.buruna.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String avatarUrl,
        String presentationMessage,
        String role,
        String status,
        BigDecimal quotaGb,
        Instant createdAt
) {
}
