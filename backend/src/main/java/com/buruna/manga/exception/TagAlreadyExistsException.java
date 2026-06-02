package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TagAlreadyExistsException extends DomainException {
    public TagAlreadyExistsException(String slug) {
        super(HttpStatus.CONFLICT, "Tag with slug already exists: " + slug);
    }
}