package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TagCategoryAlreadyExistsException extends DomainException {
    public TagCategoryAlreadyExistsException(String name) {
        super(HttpStatus.CONFLICT, "Tag category already exists: " + name);
    }
}