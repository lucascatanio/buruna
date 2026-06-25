package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class TagNotFoundException extends DomainException {

    public TagNotFoundException(UUID id) {
        super(DomainErrorType.NOT_FOUND, "Tag not found: " + id);
    }
}
