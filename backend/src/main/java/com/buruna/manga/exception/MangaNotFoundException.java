package com.buruna.manga.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class MangaNotFoundException extends LegacyHttpDomainException {

    public MangaNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Mangá não encontrado com id: " + id);
    }

    public MangaNotFoundException(String slug) {
        super(HttpStatus.NOT_FOUND, "Mangá não encontrado com slug: " + slug);
    }
}