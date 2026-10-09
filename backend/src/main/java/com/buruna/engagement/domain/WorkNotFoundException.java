package com.buruna.engagement.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class WorkNotFoundException extends DomainException {

    public WorkNotFoundException(UUID workId) {
        super(DomainErrorType.NOT_FOUND, "Mangá público não encontrado: " + workId);
    }
}
