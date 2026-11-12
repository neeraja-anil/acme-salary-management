package com.acme.salary.analytics;

import com.acme.salary.salary.SalaryChangeReason;

/** Number of salary changes for one reason, and their average % change (unrounded). */
public record ReasonSummaryRow(SalaryChangeReason reason, Long count, Double averageChangePercent) {
}
