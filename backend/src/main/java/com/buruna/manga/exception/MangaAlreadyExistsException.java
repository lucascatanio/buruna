package com.buruna.manga.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class MangaAlreadyExistsException extends LegacyHttpDomainException {

    public MangaAlreadyExistsException(String title) {
        super(HttpStatus.CONFLICT, "Já existe um mangá com o título: " + title);
    }
}