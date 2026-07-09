package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class MangaAlreadySubmittedException extends DomainException {

    public MangaAlreadySubmittedException() {
        super(DomainErrorType.CONFLICT, "Este mangá já foi submetido para aprovação");
    }
}
