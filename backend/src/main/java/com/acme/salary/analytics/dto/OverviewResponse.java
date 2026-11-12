package com.acme.salary.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Org-wide figures (US-11). Spans countries, so every amount is in USD. Average and median are null when
 * there are no active employees.
 */
public record OverviewResponse(
        String currency,
        long headcount,
        long totalPayroll,
        Long averageSalary,
        Long medianSalary,
        List<CountryHeadcount> headcountByCountry,
        List<DepartmentHeadcount> headcountByDepartment,
        LocalDate fxRatesAsOf) {

    public record CountryHeadcount(String code, String name, long headcount) {
    }

    public record DepartmentHeadcount(Long id, String name, long headcount) {
    }
}
