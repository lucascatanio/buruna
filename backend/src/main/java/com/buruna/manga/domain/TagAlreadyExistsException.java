package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class TagAlreadyExistsException extends DomainException {

    public TagAlreadyExistsException(String slug) {
        super(DomainErrorType.CONFLICT, "Tag with slug already exists: " + slug);
    }
}
