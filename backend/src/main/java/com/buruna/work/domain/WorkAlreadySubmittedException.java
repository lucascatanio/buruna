package com.buruna.work.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class WorkAlreadySubmittedException extends DomainException {

    public WorkAlreadySubmittedException() {
        super(DomainErrorType.CONFLICT, "Este mangá já foi submetido para aprovação");
    }
}
