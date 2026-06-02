package com.buruna.manga.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class TagAlreadyExistsException extends LegacyHttpDomainException {
    public TagAlreadyExistsException(String slug) {
        super(HttpStatus.CONFLICT, "Tag with slug already exists: " + slug);
    }
}