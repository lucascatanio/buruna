package com.buruna.identity.domain;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends LegacyHttpDomainException {
    public UserAlreadyExistsException(String field) {
        super(HttpStatus.CONFLICT, "Already exists an user with this " + field);
    }
}
