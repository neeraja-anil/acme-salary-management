package com.acme.salary.employee.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/employees/{id}/deactivate} (US-07). */
public record DeactivateEmployeeRequest(
        @NotNull LocalDate terminationDate,
        @NotNull Long version) {
}
