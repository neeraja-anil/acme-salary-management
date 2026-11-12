package com.acme.salary.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.function.LongUnaryOperator;

/**
 * Summary statistics for one group of salaries, all in the same currency and in whole units.
 * <p>
 * Computed in Java rather than SQL because SQLite has no MEDIAN or PERCENTILE function. At 10k rows the
 * work is a sort of at most 10k longs. Kept pure (no Spring, no database) so it can be unit-tested directly.
 * <p>
 * Percentiles use linear interpolation between the closest ranks (the same method as Excel's
 * {@code PERCENTILE.INC}, which HR will compare against), rounded half-up to whole units.
 */
public record PayStatistics(long headcount, long min, long p25, long median, long p75, long max,
                            long average, long total) {

    /** @param amounts salaries in one currency; must not be empty. Not modified. */
    public static PayStatistics of(long[] amounts) {
        if (amounts.length == 0) {
            throw new IllegalArgumentException("Cannot compute statistics for an empty group");
        }
        long[] sorted = amounts.clone();
        Arrays.sort(sorted);
        long total = 0;
        for (long amount : sorted) {
            total = Math.addExact(total, amount);
        }
        long average = BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(sorted.length), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return new PayStatistics(sorted.length, sorted[0], percentile(sorted, 0.25), percentile(sorted, 0.5),
                percentile(sorted, 0.75), sorted[sorted.length - 1], average, total);
    }

    /**
     * Applies a monotonic conversion (e.g. local currency to USD) to every amount. Only valid for a group
     * in a single currency: converting each statistic gives the same result as converting each salary,
     * apart from rounding, and is exact for the total.
     */
    public PayStatistics convert(LongUnaryOperator conversion) {
        return new PayStatistics(headcount, conversion.applyAsLong(min), conversion.applyAsLong(p25),
                conversion.applyAsLong(median), conversion.applyAsLong(p75), conversion.applyAsLong(max),
                conversion.applyAsLong(average), conversion.applyAsLong(total));
    }

    /** {@code sorted} must be sorted ascending and non-empty; {@code p} is between 0 and 1. */
    static long percentile(long[] sorted, double p) {
        double rank = p * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sorted[lower];
        }
        BigDecimal fraction = BigDecimal.valueOf(rank - lower);
        return BigDecimal.valueOf(sorted[upper] - sorted[lower])
                .multiply(fraction)
                .add(BigDecimal.valueOf(sorted[lower]))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
