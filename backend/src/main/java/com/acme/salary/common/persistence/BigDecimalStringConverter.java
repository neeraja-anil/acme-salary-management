package com.acme.salary.common.persistence;

import java.math.BigDecimal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores exact decimals (FX rates) as TEXT. SQLite has no DECIMAL type and REAL would introduce
 * floating-point drift. Applied explicitly with {@code @Convert}, not automatically.
 */
@Converter
public class BigDecimalStringConverter implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    @Override
    public BigDecimal convertToEntityAttribute(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
