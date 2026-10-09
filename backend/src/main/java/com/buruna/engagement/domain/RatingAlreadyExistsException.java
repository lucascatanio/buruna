package com.buruna.engagement.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class RatingAlreadyExistsException extends DomainException {

    public RatingAlreadyExistsException(UUID workId) {
        super(DomainErrorType.CONFLICT,
                "Você já avaliou este mangá (workId=" + workId + "). Use PUT para atualizar.");
    }
}
