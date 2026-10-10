package com.buruna.work.domain;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nomes dos objetos de um capítulo no storage, no mesmo desenho do {@link VolumeObjectName}
 * (ADR-40): o upload vai para {@code pending/chapters/{workId}/{uuid}.{cbz|cbr|pdf}}, e o finalize só
 * aceita um pendente cujo {@code workId} no caminho é o da obra do request. Depois do
 * finalize, o arquivo vai para {@code chapter-sources/}, fora do alcance da lifecycle rule de
 * {@code pending/}, até as páginas serem extraídas para {@code chapters/{chapterId}/}.
 */
public final class ChapterObjectName {

    private static final Pattern PENDING_PATTERN = Pattern.compile(
            "^pending/chapters/([0-9a-fA-F-]{36})/([0-9a-fA-F-]{36})\\.(cbz|cbr|pdf)$"
    );

    private final UUID workId;
    private final UUID fileId;
    private final ChapterSourceFormat format;

    private ChapterObjectName(UUID workId, UUID fileId, ChapterSourceFormat format) {
        this.workId = workId;
        this.fileId = fileId;
        this.format = format;
    }

    public static String pendingFor(UUID workId, ChapterSourceFormat format) {
        if (workId == null || format == null) {
            throw new IllegalArgumentException("workId e format não podem ser nulos");
        }
        return "pending/chapters/" + workId + "/" + UUID.randomUUID() + "." + format.extension();
    }

    /** Regex ancorada: path traversal, segmento extra ou obra diferente são recusados. */
    public static ChapterObjectName parsePending(String raw, UUID workId) {
        if (raw == null || workId == null) {
            throw new InvalidChapterObjectNameException(String.valueOf(raw));
        }
        Matcher matcher = PENDING_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            throw new InvalidChapterObjectNameException(raw);
        }
        UUID pathWorkId;
        UUID fileId;
        try {
            pathWorkId = UUID.fromString(matcher.group(1));
            fileId = UUID.fromString(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw new InvalidChapterObjectNameException(raw);
        }
        if (!pathWorkId.equals(workId)) {
            throw new InvalidChapterObjectNameException(raw);
        }
        ChapterSourceFormat format = ChapterSourceFormat.fromExtension(matcher.group(3))
                .orElseThrow(() -> new InvalidChapterObjectNameException(raw));
        return new ChapterObjectName(pathWorkId, fileId, format);
    }

    public ChapterSourceFormat format() {
        return format;
    }

    public String pendingObjectName() {
        return "pending/chapters/" + workId + "/" + fileId + "." + format.extension();
    }

    public String sourceObjectName() {
        return "chapter-sources/" + workId + "/" + fileId + "." + format.extension();
    }

    /** Formato de um arquivo enviado já guardado, pela extensão do nome no storage. */
    public static ChapterSourceFormat formatOfSource(String sourceObjectName) {
        int dot = sourceObjectName == null ? -1 : sourceObjectName.lastIndexOf('.');
        return ChapterSourceFormat.fromExtension(dot < 0 ? null : sourceObjectName.substring(dot + 1))
                .orElseThrow(() -> new InvalidChapterObjectNameException(String.valueOf(sourceObjectName)));
    }

    /** Prefixo das páginas extraídas de um capítulo. */
    public static String pagesPrefix(UUID chapterId) {
        return "chapters/" + chapterId + "/";
    }

    /**
     * Nome determinístico de uma página: reprocessar o mesmo capítulo sobrescreve os mesmos
     * objetos em vez de criar outros.
     */
    public static String page(UUID chapterId, int position, String extension) {
        return pagesPrefix(chapterId) + position + "." + extension;
    }
}
