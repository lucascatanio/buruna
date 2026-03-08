package com.buruna.user.exception;

import com.buruna.infra.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends DomainException {
    public UserNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "User not found: " + id);
    }
}
