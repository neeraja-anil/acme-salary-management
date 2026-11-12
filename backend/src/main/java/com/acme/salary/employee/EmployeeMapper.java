package com.acme.salary.employee;

import com.acme.salary.employee.dto.CountryRef;
import com.acme.salary.employee.dto.DepartmentRef;
import com.acme.salary.employee.dto.EmployeeDetailResponse;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import com.acme.salary.employee.dto.JobTitleRef;
import com.acme.salary.reference.CurrencyConverter;

import org.springframework.stereotype.Component;

/** Entity to API DTO mapping. Entities never leave the service layer. */
@Component
class EmployeeMapper {

    private final CurrencyConverter currencyConverter;

    EmployeeMapper(CurrencyConverter currencyConverter) {
        this.currencyConverter = currencyConverter;
    }

    EmployeeSummaryResponse toSummary(Employee e) {
        return new EmployeeSummaryResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName(),
                e.getLastName(),
                e.getEmail(),
                country(e),
                department(e),
                jobTitle(e),
                e.getJobLevel(),
                e.getStatus(),
                new EmployeeSummaryResponse.SalaryAmount(e.getCurrentSalaryAmount(), e.getCountry().getCurrencyCode(),
                        currencyConverter.toUsd(e.getCurrentSalaryAmount(), e.getCountry().getCurrencyCode())));
    }

    EmployeeDetailResponse toDetail(Employee e) {
        String currency = e.getCountry().getCurrencyCode();
        return new EmployeeDetailResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName(),
                e.getLastName(),
                e.getEmail(),
                country(e),
                department(e),
                jobTitle(e),
                e.getJobLevel(),
                e.getHireDate(),
                e.getStatus(),
                e.getTerminationDate(),
                new EmployeeDetailResponse.CurrentSalary(
                        e.getCurrentSalaryAmount(),
                        currency,
                        currencyConverter.toUsd(e.getCurrentSalaryAmount(), currency),
                        e.getCurrentSalaryEffectiveDate()),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private static CountryRef country(Employee e) {
        return new CountryRef(e.getCountry().getCode(), e.getCountry().getName(), e.getCountry().getCurrencyCode());
    }

    private static DepartmentRef department(Employee e) {
        return new DepartmentRef(e.getDepartment().getId(), e.getDepartment().getName());
    }

    private static JobTitleRef jobTitle(Employee e) {
        return new JobTitleRef(e.getJobTitle().getId(), e.getJobTitle().getName());
    }
}
