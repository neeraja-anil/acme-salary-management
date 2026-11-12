package com.acme.salary.analytics.dto;

import java.util.List;

import com.acme.salary.analytics.PayStatistics;
import com.acme.salary.employee.dto.DepartmentRef;

/**
 * Pay by department (US-13). {@code country} is null for all countries, in which case
 * {@code currency} is USD; otherwise it is that country's currency.
 */
public record DepartmentPayResponse(String currency, String country, List<Row> rows) {

    public record Row(DepartmentRef department, PayStatistics stats) {
    }
}
