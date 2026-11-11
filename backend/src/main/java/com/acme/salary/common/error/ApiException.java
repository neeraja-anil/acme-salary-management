package com.acme.salary.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that map to a documented API error (api-contracts.md section 2).
 * Each carries the HTTP status, a stable machine-readable {@code code} and optional extra properties.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;

    protected ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    /** Extra properties added to the problem response (e.g. {@code rule}). */
    public Map<String, Object> properties() {
        return Map.of();
    }
}
