package com.buruna.sourcing.application;

import com.buruna.sourcing.domain.TrackedWork;

/** Endereço público da obra na fonte, para o crédito e o link do ADM. */
final class SourceUrls {

    private SourceUrls() {
    }

    static String work(TrackedWork tracked) {
        return switch (tracked.getSource()) {
            case MANGADEX -> "https://mangadex.org/title/" + tracked.getExternalId();
        };
    }
}
