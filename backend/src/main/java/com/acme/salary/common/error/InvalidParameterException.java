package com.acme.salary.common.error;

import org.springframework.http.HttpStatus;

/** 400: an invalid query parameter, e.g. an unknown sort field. */
public class InvalidParameterException extends ApiException {

    public InvalidParameterException(String detail) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid parameter", detail);
    }
}
