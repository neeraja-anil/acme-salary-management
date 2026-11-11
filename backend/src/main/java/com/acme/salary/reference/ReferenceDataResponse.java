package com.acme.salary.reference;

import java.time.LocalDate;
import java.util.List;

/** All lookups in one response; the UI fetches it once and caches it (api-contracts.md section 4). */
public record ReferenceDataResponse(
        List<CountryItem> countries,
        List<DepartmentItem> departments,
        List<JobTitleItem> jobTitles,
        List<JobLevelItem> jobLevels,
        List<String> salaryChangeReasons,
        List<FxRateItem> fxRates) {

    public record CountryItem(String code, String name, String currency) {
    }

    public record DepartmentItem(Long id, String name) {
    }

    public record JobTitleItem(Long id, String name, Long departmentId) {
    }

    public record JobLevelItem(String code, String name) {
    }

    public record FxRateItem(String currency, String rateToUsd, LocalDate asOf) {
    }
}
