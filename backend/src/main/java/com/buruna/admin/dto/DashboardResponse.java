package com.buruna.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        long activeUsers,
        BigDecimal totalStorageUsedGb,
        List<UserStorageResponse> storageByUser
) {}