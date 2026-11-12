package com.acme.salary.employee;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.acme.salary.common.error.InvalidParameterException;

import org.springframework.data.domain.Sort;

/**
 * Allow-listed sort fields for the directory. Parses {@code "salary,desc"} into a {@link Sort}.
 * A final sort on id keeps paging stable when values tie (e.g. many employees with the same salary).
 * <p>
 * {@code salary} sorts by the local amount; {@code salaryUsd} by the amount converted to USD, which is the only
 * meaningful order when the list mixes currencies. USD is not a stored column, so that sort is an expression
 * ({@link EmployeeSpecifications#orderedBySalaryUsd}) rather than a property.
 */
enum EmployeeSort {
    NAME("name", "lastName", "firstName"),
    DEPARTMENT("department", "department.name"),
    HIRE_DATE("hireDate", "hireDate"),
    SALARY("salary", "currentSalaryAmount"),
    SALARY_USD("salaryUsd");

    /** {@code salaryUsdDirection} is set only when sorting by USD salary; {@code sort} is then unsorted. */
    record Parsed(Sort sort, Sort.Direction salaryUsdDirection) {
        boolean bySalaryUsd() {
            return salaryUsdDirection != null;
        }
    }

    private final String apiName;
    private final String[] properties;

    EmployeeSort(String apiName, String... properties) {
        this.apiName = apiName;
        this.properties = properties;
    }

    static Parsed parse(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length > 2) {
            throw new InvalidParameterException("sort must look like '<field>,<asc|desc>'");
        }
        EmployeeSort field = Arrays.stream(values())
                .filter(s -> s.apiName.equals(parts[0].trim()))
                .findFirst()
                .orElseThrow(() -> new InvalidParameterException(
                        "Unknown sort field '" + parts[0].trim() + "'. Allowed: " + allowedFields()));
        Sort.Direction direction = parts.length == 2 ? direction(parts[1]) : Sort.Direction.ASC;
        if (field == SALARY_USD) {
            return new Parsed(Sort.unsorted(), direction);
        }
        return new Parsed(Sort.by(direction, field.properties).and(Sort.by(Sort.Direction.ASC, "id")), null);
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
