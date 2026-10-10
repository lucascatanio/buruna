package com.buruna.work.application;

import java.util.Optional;
import java.util.UUID;

/** Formato dos argumentos que o backend passa para o Job e o Job lê de volta. */
public final class IngestJobArgs {

    public static final String CHAPTER_OPTION = "ingest.chapter-id";

    private IngestJobArgs() {
    }

    static String chapter(UUID chapterId) {
        return "--" + CHAPTER_OPTION + "=" + chapterId;
    }

    public static Optional<UUID> parseChapterId(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
