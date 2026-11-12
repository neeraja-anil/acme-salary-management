package com.acme.salary.bulkimport;

import java.util.List;

/** The HR import file's columns (api-contracts.md section 5a). Column order doesn't matter; header names do. */
final class ImportFormat {

    static final String FIRST_NAME = "first_name";
    static final String LAST_NAME = "last_name";
    static final String EMAIL = "email";
    static final String COUNTRY_CODE = "country_code";
    static final String DEPARTMENT = "department";
    static final String JOB_TITLE = "job_title";
    static final String JOB_LEVEL = "job_level";
    static final String HIRE_DATE = "hire_date";
    static final String ANNUAL_SALARY = "annual_salary";
    static final String CURRENCY = "currency";
    static final String SALARY_EFFECTIVE_DATE = "salary_effective_date";
    static final String SALARY_REASON = "salary_reason";

    static final List<String> REQUIRED = List.of(FIRST_NAME, LAST_NAME, EMAIL, COUNTRY_CODE, DEPARTMENT, JOB_TITLE,
            JOB_LEVEL, HIRE_DATE, ANNUAL_SALARY, CURRENCY);
    static final List<String> OPTIONAL = List.of(SALARY_EFFECTIVE_DATE, SALARY_REASON);
    static final List<String> ALL = List.of(FIRST_NAME, LAST_NAME, EMAIL, COUNTRY_CODE, DEPARTMENT, JOB_TITLE,
            JOB_LEVEL, HIRE_DATE, ANNUAL_SALARY, CURRENCY, SALARY_EFFECTIVE_DATE, SALARY_REASON);

    /** One example row for the template. The email is on example.com so it never clashes with real data. */
    static final List<String> EXAMPLE = List.of("Lena", "Fischer", "lena.fischer@example.com", "DE", "Engineering",
            "Software Engineer", "L3", "2021-03-15", "78000", "EUR", "2025-04-01", "ANNUAL_REVIEW");

    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final int MAX_ROWS = 10_000;
    /** A completely wrong file shouldn't produce a huge response; every invalid row is still counted. */
    static final int MAX_ERRORS_LISTED = 500;
    static final int SAMPLE_ROWS = 5;

    private ImportFormat() {
    }
}
