package com.buruna.manga.application.maintenance;

/** Contagens de uma execução de {@link DeleteOrphanVolumeFilesUseCase}. */
public record OrphanVolumeFilesResult(int analyzed, int orphans, int deleted) {}
