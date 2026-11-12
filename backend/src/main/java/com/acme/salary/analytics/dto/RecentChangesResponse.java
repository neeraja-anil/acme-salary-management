package com.acme.salary.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.acme.salary.common.web.PageResponse;
import com.acme.salary.salary.SalaryChangeReason;

/**
 * Salary changes with an effective date in [from, to] (US-15). The summary covers every matching change;
 * {@code changes} is one page of them, newest first. Percentages are unitless, so they can be averaged
 * across currencies. {@code averageChangePercent} is null when there are no changes.
 */
public record RecentChangesResponse(int days, LocalDate from, LocalDate to, Summary summary,
                                    PageResponse<Change> changes) {

    public record Summary(long changeCount, BigDecimal averageChangePercent, List<ReasonSummary> byReason) {
    }

    public record ReasonSummary(SalaryChangeReason reason, long count, BigDecimal averageChangePercent) {
    }

    public record Change(Long employeeId, String employeeCode, String employeeName, String country,
                         String currency, Long previousAmount, long amount, BigDecimal changePercent,
                         SalaryChangeReason reason, LocalDate effectiveDate, String changedBy,
                         Instant changedAt) {
    }
}
