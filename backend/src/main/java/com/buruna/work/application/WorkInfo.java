package com.buruna.work.application;

import java.util.UUID;

public record WorkInfo(UUID id, String slug, String title, String coverUrl) {}
