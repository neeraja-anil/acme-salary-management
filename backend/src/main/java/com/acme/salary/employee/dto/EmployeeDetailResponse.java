package com.acme.salary.employee.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.reference.JobLevel;

/** Full employee profile (US-04). {@code version} must be sent back on updates. */
public record EmployeeDetailResponse(
        Long id,
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        CountryRef country,
        DepartmentRef department,
        JobTitleRef jobTitle,
        JobLevel jobLevel,
        LocalDate hireDate,
        EmployeeStatus status,
        LocalDate terminationDate,
        CurrentSalary currentSalary,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    /** Current salary in local currency, with its USD equivalent at the reporting rate. */
    public record CurrentSalary(Long amount, String currency, Long amountUsd, LocalDate effectiveDate) {
    }
}
