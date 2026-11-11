package com.acme.salary.common.error;

import org.springframework.http.HttpStatus;

/** 409: the request conflicts with the current state (duplicate email, stale version). */
public class ConflictException extends ApiException {

    public static final String DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String STALE_VERSION = "STALE_VERSION";

    public ConflictException(String code, String detail) {
        super(HttpStatus.CONFLICT, code, "Conflict", detail);
    }

    public static ConflictException duplicateEmail(String email) {
        return new ConflictException(DUPLICATE_EMAIL, "Email " + email + " is already used by another employee");
    }

    public static ConflictException staleVersion() {
        return new ConflictException(STALE_VERSION,
                "This record was changed by someone else. Reload to see the latest version.");
    }
}
