package com.buruna.manga.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class VolumeNotFoundException extends LegacyHttpDomainException {

    public VolumeNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Volume não encontrado: " + id);
    }
}