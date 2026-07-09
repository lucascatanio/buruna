package com.buruna.identity.web;

import com.buruna.identity.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull Role role) {
}
