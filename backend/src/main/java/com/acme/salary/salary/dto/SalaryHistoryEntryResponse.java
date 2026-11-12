package com.acme.salary.salary.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.acme.salary.salary.SalaryChangeReason;

/** One salary record (US-09). {@code changePercent} is null for the first salary. */
public record SalaryHistoryEntryResponse(
        Long id,
        Long amount,
        String currency,
        Long previousAmount,
        BigDecimal changePercent,
        LocalDate effectiveDate,
        SalaryChangeReason reason,
        String note,
        String changedBy,
        Instant changedAt) {
}
