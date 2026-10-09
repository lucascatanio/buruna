package com.buruna.work.application;

import java.util.UUID;

public record VolumeInfo(UUID volumeId, int volumeNumber, UUID workId) {}
