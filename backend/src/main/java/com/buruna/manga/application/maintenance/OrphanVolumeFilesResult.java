package com.buruna.manga.application.maintenance;

/**
 * Contagens de uma execução de {@link DeleteOrphanVolumeFilesUseCase}. {@code aborted}: a trava
 * de proporção barrou a deleção (nada apagado).
 */
public record OrphanVolumeFilesResult(int analyzed, int orphans, int deleted, boolean dryRun, boolean aborted) {}
