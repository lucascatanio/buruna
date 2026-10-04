package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class PendingUploadNotFoundException extends DomainException {

    public PendingUploadNotFoundException() {
        super(DomainErrorType.NOT_FOUND, "Upload pendente não encontrado");
    }
}
