package com.buruna.manga.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public class TagCategoryNotFoundException extends LegacyHttpDomainException {
    public TagCategoryNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Tag category not found: " + id);
    }
}