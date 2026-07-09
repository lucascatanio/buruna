package com.buruna.manga.application;

import java.util.UUID;

public record MangaInfo(UUID id, String slug, String title, String coverUrl) {}
