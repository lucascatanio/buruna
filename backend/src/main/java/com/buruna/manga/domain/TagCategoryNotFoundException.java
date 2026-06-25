package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

import java.util.UUID;

public final class TagCategoryNotFoundException extends DomainException {

    public TagCategoryNotFoundException(UUID id) {
        super(DomainErrorType.NOT_FOUND, "Tag category not found: " + id);
    }
}
