package com.acme.salary.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One field's change. {@code from} is omitted when there was no previous value
 * (e.g. every field of a newly created employee).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldChange(Object from, Object to) {
}
