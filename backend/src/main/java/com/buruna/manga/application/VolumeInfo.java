package com.buruna.manga.application;

import java.util.UUID;

public record VolumeInfo(UUID volumeId, int volumeNumber, UUID mangaId) {}
