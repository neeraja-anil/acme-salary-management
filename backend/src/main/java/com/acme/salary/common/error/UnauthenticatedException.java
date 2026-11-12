package com.acme.salary.common.error;

import org.springframework.http.HttpStatus;

/** Wrong username or password on sign-in. The message never says which one was wrong (US-18). */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Unauthenticated", detail);
    }
}
