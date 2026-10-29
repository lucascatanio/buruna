package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidLanguageException extends DomainException {

    public InvalidLanguageException(String tag) {
        super(DomainErrorType.VALIDATION, "Idioma inválido: " + tag);
    }
}
