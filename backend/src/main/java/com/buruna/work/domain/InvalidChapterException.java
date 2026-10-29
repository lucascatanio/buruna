package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

/** Capítulo em estado ou com conteúdo que não permite a operação pedida. */
public final class InvalidChapterException extends DomainException {

    public InvalidChapterException(String message) {
        super(DomainErrorType.VALIDATION, message);
    }
}
