package com.buruna.sourcing.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/** A fonte não respondeu ou respondeu algo inesperado durante um pedido do ADM. */
public final class SourceUnavailableException extends DomainException {

    public SourceUnavailableException() {
        super(DomainErrorType.UNPROCESSABLE, "O MangaDex não respondeu. Tente de novo em alguns minutos.");
    }
}
