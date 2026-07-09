package com.buruna.engagement.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class RatingAlreadyExistsException extends DomainException {

    public RatingAlreadyExistsException(UUID mangaId) {
        super(DomainErrorType.CONFLICT,
                "Você já avaliou este mangá (mangaId=" + mangaId + "). Use PUT para atualizar.");
    }
}
