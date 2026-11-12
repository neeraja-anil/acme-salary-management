package com.acme.salary.seed;

import java.time.LocalDate;
import java.util.List;

import com.acme.salary.reference.JobLevel;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.seed.SeedReferenceData.CountryRef;
import com.acme.salary.seed.SeedReferenceData.DepartmentRef;
import com.acme.salary.seed.SeedReferenceData.JobTitleRef;

/**
 * One generated employee with their salary history, oldest first. The first salary is always the
 * {@code NEW_HIRE} row on the hire date; {@code terminationDate} is null for active employees.
 */
record SeedEmployee(String employeeCode, String firstName, String lastName, String email, CountryRef country,
                    DepartmentRef department, JobTitleRef jobTitle, JobLevel jobLevel, LocalDate hireDate,
                    LocalDate terminationDate, List<Salary> salaries) {

    record Salary(long amount, Long previousAmount, LocalDate effectiveDate, SalaryChangeReason reason) {
    }

    Salary currentSalary() {
        return salaries.get(salaries.size() - 1);
    }

    boolean isTerminated() {
        return terminationDate != null;
    }
}
