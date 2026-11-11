package com.acme.salary.common.error;

/** One invalid field in a {@code VALIDATION_FAILED} / {@code INVALID_PARAMETER} response. */
public record FieldError(String field, String message) {
}
