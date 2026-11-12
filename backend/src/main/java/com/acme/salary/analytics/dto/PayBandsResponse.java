package com.acme.salary.analytics.dto;

import java.util.List;

import com.acme.salary.analytics.PayStatistics;
import com.acme.salary.employee.dto.JobTitleRef;
import com.acme.salary.reference.JobLevel;

/**
 * Pay bands by job title and level (US-14). Same currency rule as {@link DepartmentPayResponse}.
 * {@code lowSample} flags bands with fewer than 3 people, whose median is not a reliable band.
 */
public record PayBandsResponse(String currency, String country, List<Row> rows) {

    public record Row(JobTitleRef jobTitle, JobLevel jobLevel, PayStatistics stats, boolean lowSample) {
    }
}
