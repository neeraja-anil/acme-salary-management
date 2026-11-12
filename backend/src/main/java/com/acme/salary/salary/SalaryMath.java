package com.acme.salary.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small, pure salary calculations shared by history and analytics. */
public final class SalaryMath {

    private SalaryMath() {
    }

    /**
     * Percentage change from {@code previous} to {@code current}, rounded half-up to 2 decimals.
     * Returns {@code null} when there is no previous salary (a first salary is not a change).
     */
    public static BigDecimal changePercent(Long previous, long current) {
        if (previous == null || previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }
}
