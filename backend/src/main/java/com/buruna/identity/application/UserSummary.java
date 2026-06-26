package com.buruna.identity.application;

import java.util.UUID;

/**
 * Projeção pública mínima de um usuário, exposta para outros contextos consumirem via
 * use case (ADR-39). Evita que contextos externos importem a entidade {@code User} ou o
 * {@code UserRepository} de identity.
 */
public record UserSummary(UUID id, String username, String email) {
}
