package com.buruna.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateQuotaRequest(
        @NotNull @DecimalMin("0.1") BigDecimal quotaGb
) {}
