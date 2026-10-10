package com.buruna.shared.media;

import java.util.Arrays;
import java.util.Objects;

/** Página extraída de um arquivo de quadrinhos. {@code position} começa em 1. */
public record ExtractedPage(int position, byte[] content, String contentType, String extension,
                            int width, int height) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExtractedPage other)) {
            return false;
        }
        return position == other.position && width == other.width && height == other.height
                && Arrays.equals(content, other.content)
                && Objects.equals(contentType, other.contentType)
                && Objects.equals(extension, other.extension);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(position, contentType, extension, width, height) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "ExtractedPage[position=" + position + ", contentSize=" + (content == null ? 0 : content.length)
                + ", contentType=" + contentType + ", extension=" + extension
                + ", width=" + width + ", height=" + height + "]";
    }
}
