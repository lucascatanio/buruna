package com.buruna.admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UserStorageResponse(
        UUID userId,
        String username,
        BigDecimal usedGb,
        BigDecimal quotaGb
) {
}