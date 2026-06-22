package com.buruna.identity.domain;

import com.buruna.shared.exception.LegacyHttpDomainException;
import org.springframework.http.HttpStatus;

public class UserNotPendingException extends LegacyHttpDomainException {
    public UserNotPendingException() {
        super(HttpStatus.CONFLICT, "User is not in PENDING status");
    }
}
