package com.buruna.user.exception;

import com.buruna.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class UserNotPendingException extends DomainException {
    public UserNotPendingException() {
        super(HttpStatus.CONFLICT, "User is not in PENDING status");
    }
}
