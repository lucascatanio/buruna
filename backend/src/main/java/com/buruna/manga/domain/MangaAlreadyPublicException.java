package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class MangaAlreadyPublicException extends DomainException {

    public MangaAlreadyPublicException() {
        super(DomainErrorType.VALIDATION, "Este mangá já está na biblioteca pública");
    }
}
