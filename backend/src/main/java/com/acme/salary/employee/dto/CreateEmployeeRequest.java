package com.acme.salary.employee.dto;

import java.time.LocalDate;

import com.acme.salary.reference.JobLevel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/employees} (US-05). Currency is derived from the country, never sent. */
public record CreateEmployeeRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "must be a 2-letter country code") String countryCode,
        @NotNull Long departmentId,
        @NotNull Long jobTitleId,
        @NotNull JobLevel jobLevel,
        @NotNull LocalDate hireDate,
        @NotNull @Positive @Max(100_000_000) Long startingSalary) {
}
