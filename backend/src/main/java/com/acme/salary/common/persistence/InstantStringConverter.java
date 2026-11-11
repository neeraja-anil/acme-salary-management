package com.acme.salary.common.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link Instant} as ISO-8601 UTC TEXT with second precision ({@code 2026-04-01T10:12:44Z}).
 * A fixed precision keeps string ordering identical to time ordering.
 */
@Converter(autoApply = true)
public class InstantStringConverter implements AttributeConverter<Instant, String> {

    @Override
    public String convertToDatabaseColumn(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    @Override
    public Instant convertToEntityAttribute(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
