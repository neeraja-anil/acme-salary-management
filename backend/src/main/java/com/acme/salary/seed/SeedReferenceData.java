package com.acme.salary.seed;

import java.util.List;

/**
 * The lookups the seed draws from, read once from the database (seeded there by the V2 migration).
 * Plain records rather than JPA entities, so the generator stays a pure function that tests can call directly.
 */
record SeedReferenceData(List<CountryRef> countries, List<DepartmentRef> departments, List<JobTitleRef> jobTitles) {

    record CountryRef(String code, String currencyCode) {
    }

    record DepartmentRef(long id, String name) {
    }

    record JobTitleRef(long id, String name, long departmentId) {
    }

    List<JobTitleRef> jobTitlesIn(DepartmentRef department) {
        return jobTitles.stream().filter(t -> t.departmentId() == department.id()).toList();
    }
}
