package com.buruna.user.exception;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class UserNotActiveException extends LegacyHttpDomainException {
    public UserNotActiveException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
