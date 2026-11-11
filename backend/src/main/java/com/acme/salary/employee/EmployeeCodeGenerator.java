package com.acme.salary.employee;

import org.springframework.stereotype.Component;

/**
 * Issues sequential, human-friendly employee codes ({@code EMP-000001}).
 * Called inside the create transaction. SQLite allows one writer at a time, and the UNIQUE
 * constraint on {@code employee_code} is the final guard against a duplicate.
 */
@Component
public class EmployeeCodeGenerator {

    static final String PREFIX = "EMP-";
    private static final int DIGITS = 6;

    private final EmployeeRepository employeeRepository;

    public EmployeeCodeGenerator(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public String next() {
        long lastNumber = employeeRepository.findMaxEmployeeCode()
                .map(code -> Long.parseLong(code.substring(PREFIX.length())))
                .orElse(0L);
        return format(lastNumber + 1);
    }

    static String format(long number) {
        return PREFIX + String.format("%0" + DIGITS + "d", number);
    }
}
