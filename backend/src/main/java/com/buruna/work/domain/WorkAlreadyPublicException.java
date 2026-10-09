package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class WorkAlreadyPublicException extends DomainException {

    public WorkAlreadyPublicException() {
        super(DomainErrorType.VALIDATION, "Este mangá já está na biblioteca pública");
    }
}
