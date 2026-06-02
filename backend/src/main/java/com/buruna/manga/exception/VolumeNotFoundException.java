package com.buruna.manga.exception;

import com.buruna.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class VolumeNotFoundException extends DomainException {

    public VolumeNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Volume não encontrado: " + id);
    }
}