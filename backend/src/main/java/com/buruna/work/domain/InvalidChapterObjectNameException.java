package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class InvalidChapterObjectNameException extends DomainException {

    public InvalidChapterObjectNameException(String objectName) {
        super(DomainErrorType.VALIDATION, "objectName de capítulo inválido: " + objectName);
    }
}
