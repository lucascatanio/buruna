package com.buruna.manga.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public class TagNotFoundException extends LegacyHttpDomainException {
    public TagNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Tag not found: " + id);
    }
}