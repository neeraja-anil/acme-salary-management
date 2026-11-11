package com.acme.salary.employee;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.acme.salary.common.error.InvalidParameterException;

import org.springframework.data.domain.Sort;

/**
 * Allow-listed sort fields for the directory. Parses {@code "salary,desc"} into a {@link Sort}.
 * A final sort on id keeps paging stable when values tie (e.g. many employees with the same salary).
 */
enum EmployeeSort {
    NAME("name", "lastName", "firstName"),
    DEPARTMENT("department", "department.name"),
    HIRE_DATE("hireDate", "hireDate"),
    SALARY("salary", "currentSalaryAmount");

    private final String apiName;
    private final String[] properties;

    EmployeeSort(String apiName, String... properties) {
        this.apiName = apiName;
        this.properties = properties;
    }

    static Sort parse(String value) {
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
        return Sort.by(direction, field.properties).and(Sort.by(Sort.Direction.ASC, "id"));
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
