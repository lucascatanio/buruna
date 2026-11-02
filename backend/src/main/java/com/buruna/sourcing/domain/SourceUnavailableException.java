package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/** A fonte não respondeu ou respondeu algo inesperado durante um pedido do ADM. */
public final class SourceUnavailableException extends DomainException {

    public SourceUnavailableException() {
        this("MangaDex");
    }

    /** {@code sourceName}: como o ADM conhece a fonte ("MangaDex", "AniList"). */
    public SourceUnavailableException(String sourceName) {
        super(DomainErrorType.UNPROCESSABLE, "O " + sourceName + " não respondeu. Tente de novo em alguns minutos.");
    }
}
