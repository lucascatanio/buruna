package com.buruna.manga.exception;

import com.buruna.infra.exception.DomainException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public class TagNotFoundException extends DomainException {
    public TagNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Tag not found: " + id);
    }
}