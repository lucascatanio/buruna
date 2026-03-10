package com.buruna.manga.exception;

import com.buruna.infra.exception.DomainException;
import org.springframework.http.HttpStatus;

public class MangaAlreadyExistsException extends DomainException {

    public MangaAlreadyExistsException(String title) {
        super(HttpStatus.CONFLICT, "Já existe um mangá com o título: " + title);
    }
}