package com.buruna.work.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Formato do arquivo enviado para um capítulo de imagens. Define a extensão no storage e o Content-Type assinado. */
public enum ChapterSourceFormat {
    CBZ("cbz", "application/vnd.comicbook+zip"),
    CBR("cbr", "application/vnd.comicbook-rar"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;

    ChapterSourceFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ChapterSourceFormat> fromExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.extension.equals(normalized)).findFirst();
    }
}
