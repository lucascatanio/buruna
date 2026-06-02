package com.buruna.manga.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class TagCategoryAlreadyExistsException extends LegacyHttpDomainException {
    public TagCategoryAlreadyExistsException(String name) {
        super(HttpStatus.CONFLICT, "Tag category already exists: " + name);
    }
}