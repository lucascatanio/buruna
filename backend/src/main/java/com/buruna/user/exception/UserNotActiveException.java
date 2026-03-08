package com.buruna.user.exception;

import com.buruna.infra.exception.DomainException;
import org.springframework.http.HttpStatus;

public class UserNotActiveException extends DomainException {
    public UserNotActiveException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
