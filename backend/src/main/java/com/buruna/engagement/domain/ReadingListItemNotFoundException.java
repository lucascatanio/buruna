package com.buruna.engagement.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class ReadingListItemNotFoundException extends DomainException {

    public ReadingListItemNotFoundException(UUID mangaId) {
        super(DomainErrorType.NOT_FOUND, "Item não encontrado na lista de leitura para o mangá " + mangaId);
    }
}
