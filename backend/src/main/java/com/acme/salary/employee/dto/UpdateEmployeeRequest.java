package com.acme.salary.employee.dto;

import com.acme.salary.reference.JobLevel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/employees/{id}} (US-06). Country, hire date and salary are deliberately absent:
 * country is fixed after creation and salary changes only through the salary endpoint.
 * {@code version} is the value the client loaded, for optimistic locking.
 */
public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull Long departmentId,
        @NotNull Long jobTitleId,
        @NotNull JobLevel jobLevel,
        @NotNull Long version) {
}
