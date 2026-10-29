package com.buruna.work.application.admin;

import java.util.UUID;

/**
 * Projeção pública de uso de armazenamento por dono, exposta para o dashboard de
 * admin (ADR-39) sem vazar {@code VolumeStorageProjection} (persistence) para fora
 * do contexto work.
 */
public record OwnerStorageUsage(UUID ownerId, long totalBytes) {
}
