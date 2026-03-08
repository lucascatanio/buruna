package com.buruna.user.exception;

import com.buruna.infra.exception.DomainException;
import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends DomainException {
    public UserAlreadyExistsException(String field) {
        super(HttpStatus.CONFLICT, "Already exists an user with this " + field);
    }
}
