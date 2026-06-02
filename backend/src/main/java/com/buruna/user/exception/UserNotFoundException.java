package com.buruna.user.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends LegacyHttpDomainException {
    public UserNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "User not found: " + id);
    }
}
