package com.acme.salary.analytics;

import java.time.Instant;
import java.time.LocalDate;

import com.acme.salary.salary.SalaryChangeReason;

/** Projection of one salary change joined with the employee it belongs to. */
public record RecentChangeRow(Long employeeId, String employeeCode, String firstName, String lastName,
                              String countryCode, String currency, Long previousAmount, long amount,
                              SalaryChangeReason reason, LocalDate effectiveDate, String changedBy,
                              Instant changedAt) {
}
