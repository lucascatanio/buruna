package com.buruna.work.application;

import java.util.UUID;

public record VolumeReadInfo(UUID volumeId, String fileUrl, UUID workId) {}
