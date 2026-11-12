package com.acme.salary.employee.dto;

import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.reference.JobLevel;

/** One row of the employee directory (US-01). */
public record EmployeeSummaryResponse(
        Long id,
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        CountryRef country,
        DepartmentRef department,
        JobTitleRef jobTitle,
        JobLevel jobLevel,
        EmployeeStatus status,
        SalaryAmount currentSalary) {

    /** Annual salary in local currency, with its USD equivalent at the fixed reporting rate. */
    public record SalaryAmount(Long amount, String currency, Long amountUsd) {
    }
}
