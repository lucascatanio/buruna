package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class MangaNotFoundException extends DomainException {

    public MangaNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Mangá não encontrado com id: " + id);
    }

    public MangaNotFoundException(String slug) {
        super(HttpStatus.NOT_FOUND, "Mangá não encontrado com slug: " + slug);
    }
}