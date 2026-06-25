package com.buruna.manga.domain;

import com.buruna.shared.exception.DomainErrorType;
import com.buruna.shared.exception.DomainException;

public final class TagCategoryAlreadyExistsException extends DomainException {

    public TagCategoryAlreadyExistsException(String name) {
        super(DomainErrorType.CONFLICT, "Tag category already exists: " + name);
    }
}
