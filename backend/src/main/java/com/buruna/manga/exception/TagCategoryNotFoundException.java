package com.buruna.manga.exception;

import com.buruna.infra.exception.DomainException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public class TagCategoryNotFoundException extends DomainException {
    public TagCategoryNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Tag category not found: " + id);
    }
}