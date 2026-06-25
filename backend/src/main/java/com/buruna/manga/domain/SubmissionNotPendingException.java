package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class SubmissionNotPendingException extends DomainException {

    public SubmissionNotPendingException() {
        super(DomainErrorType.VALIDATION, "Submissão não está pendente");
    }
}
