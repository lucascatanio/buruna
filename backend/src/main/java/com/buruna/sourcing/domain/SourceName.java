package com.buruna.sourcing.domain;

/**
 * Fonte externa de onde as obras são importadas: o MangaDex pela API (ADR-47) ou qualquer site
 * que o manga-downloader suporte (ADR-54), identificado pela URL da série.
 */
public enum SourceName {
    MANGADEX,
    MANGA_DOWNLOADER
}
