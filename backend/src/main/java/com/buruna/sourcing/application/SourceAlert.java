package com.buruna.sourcing.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Algo numa versão de capítulo que pede decisão do ADM. {@code sourceChapterId} é a versão sobre
 * a qual as ações agem (tentar de novo, baixar de novo, usar esta versão).
 */
public record SourceAlert(
        UUID sourceChapterId,
        UUID trackedWorkId,
        Type type,
        String language,
        String number,
        String title,
        String groupNames,
        String detail,
        OffsetDateTime detectedAt
) {

    public enum Type {
        /** Falhou {@code MAX_ATTEMPTS} vezes; só volta pelo botão. */
        FAILED,
        /** Sumiu da fonte; o capítulo baixado continua no ar. */
        REMOVED_AT_SOURCE,
        /** A fonte trocou as páginas de uma versão já baixada. */
        PAGES_CHANGED,
        /** O grupo preferido publicou um número que já tinha entrado por outro grupo. */
        PREFERRED_AVAILABLE
    }
}
