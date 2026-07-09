package com.buruna.identity.web;

import com.buruna.identity.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull UserStatus status) {
}
