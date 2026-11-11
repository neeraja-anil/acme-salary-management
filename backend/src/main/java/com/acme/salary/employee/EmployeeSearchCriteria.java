package com.acme.salary.employee;

import java.util.List;

import com.acme.salary.reference.JobLevel;

/**
 * Directory filters. Filters combine with AND; several values for one filter combine with OR.
 * Empty lists mean "no filter".
 */
public record EmployeeSearchCriteria(
        String query,
        List<String> countryCodes,
        List<Long> departmentIds,
        List<Long> jobTitleIds,
        List<JobLevel> jobLevels,
        StatusFilter status) {

    public EmployeeSearchCriteria {
        countryCodes = countryCodes == null ? List.of() : List.copyOf(countryCodes);
        departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
        jobTitleIds = jobTitleIds == null ? List.of() : List.copyOf(jobTitleIds);
        jobLevels = jobLevels == null ? List.of() : List.copyOf(jobLevels);
        status = status == null ? StatusFilter.ACTIVE : status;
    }
}
