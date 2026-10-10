package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class UnsupportedChapterSourceException extends DomainException {

    public UnsupportedChapterSourceException(String message) {
        super(DomainErrorType.VALIDATION, message);
    }
}
