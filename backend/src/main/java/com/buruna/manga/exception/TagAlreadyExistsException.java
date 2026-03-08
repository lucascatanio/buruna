package com.buruna.manga.exception;

import com.buruna.infra.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TagAlreadyExistsException extends DomainException {
    public TagAlreadyExistsException(String slug) {
        super(HttpStatus.CONFLICT, "Tag with slug already exists: " + slug);
    }
}