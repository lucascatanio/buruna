package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.SourceName;

/** Como o ADM chama cada fonte nas mensagens ("O MangaDex não respondeu", "O site não respondeu"). */
final class SourceLabels {

    private SourceLabels() {
    }

    static String of(SourceName source) {
        return switch (source) {
            case MANGADEX -> "MangaDex";
            case MANGA_DOWNLOADER -> "site";
        };
    }
}
