package com.buruna.manga.application;

import java.util.UUID;

public record VolumeReadInfo(UUID volumeId, String fileUrl, UUID mangaId) {}
