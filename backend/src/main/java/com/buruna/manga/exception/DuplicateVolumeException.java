package com.buruna.manga.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class DuplicateVolumeException extends LegacyHttpDomainException {

    public DuplicateVolumeException() {
        super(HttpStatus.CONFLICT, "Este arquivo já foi enviado anteriormente (hash duplicado)");
    }

    public DuplicateVolumeException(int volumeNumber) {
        super(HttpStatus.CONFLICT, "Já existe o volume " + volumeNumber + " para este mangá");
    }
}