package com.buruna.engagement.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class RatingNotFoundException extends DomainException {

    public RatingNotFoundException(UUID mangaId) {
        super(DomainErrorType.NOT_FOUND,
                "Avaliação não encontrada para o mangá " + mangaId + ". Use POST para avaliar.");
    }
}
