package com.acme.salary.analytics;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.acme.salary.common.error.InvalidParameterException;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

/**
 * Allow-listed sorts for the recent changes list: {@code "changePercent,desc"} and the like.
 * The default, {@code effectiveDate,desc}, is newest first. A final sort on the row id keeps paging stable
 * when values tie. Amounts are not sortable: the list mixes currencies, so they can't be compared, but a
 * percentage can.
 * <p>
 * The change percentage is not a stored column, so it is an expression on the query's {@code h} alias
 * (the same formula as {@link AnalyticsRepository#summariseRecentChangesByReason}).
 */
enum RecentChangesSort {
    EFFECTIVE_DATE("effectiveDate", "h.effectiveDate"),
    CHANGE_PERCENT("changePercent", "(h.amount - h.previousAmount) * 100.0 / h.previousAmount");

    static final String DEFAULT = "effectiveDate,desc";

    private final String apiName;
    private final String expression;

    RecentChangesSort(String apiName, String expression) {
        this.apiName = apiName;
        this.expression = expression;
    }

    static Sort parse(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length > 2) {
            throw new InvalidParameterException("sort must look like '<field>,<asc|desc>'");
        }
        RecentChangesSort field = Arrays.stream(values())
                .filter(s -> s.apiName.equals(parts[0].trim()))
                .findFirst()
                .orElseThrow(() -> new InvalidParameterException(
                        "Unknown sort field '" + parts[0].trim() + "'. Allowed: " + allowedFields()));
        Sort.Direction direction = parts.length == 2 ? direction(parts[1]) : Sort.Direction.DESC;
        return JpaSort.unsafe(direction, field.expression).andUnsafe(direction, "h.id");
    }

    private static Sort.Direction direction(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidParameterException("Sort direction must be 'asc' or 'desc'");
        };
    }

    private static String allowedFields() {
        return Arrays.stream(values()).map(s -> s.apiName).collect(Collectors.joining(", "));
    }
}
