package com.acme.salary.salary.dto;

import java.time.LocalDate;

import com.acme.salary.salary.SalaryChangeReason;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/employees/{id}/salaries} (US-08). Currency is not sent: it is fixed by
 * the employee's country. {@code version} is the employee version the client loaded.
 */
@ValidSalaryChange
public record SalaryChangeRequest(
        @NotNull @Positive @Max(100_000_000) Long amount,
        @NotNull LocalDate effectiveDate,
        @NotNull SalaryChangeReason reason,
        @Size(max = 500) String note,
        @NotNull Long version) {
}
