package com.buruna.sourcing.application;

/** Grupo que publicou versões de uma obra acompanhada. {@code ignored}: as versões dele não entram. */
public record TrackedGroupResponse(String id, String name, int versionCount, boolean ignored) {
}
