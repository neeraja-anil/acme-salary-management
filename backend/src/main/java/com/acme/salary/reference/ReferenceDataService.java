package com.acme.salary.reference;

import java.util.Arrays;

import com.acme.salary.reference.ReferenceDataResponse.CountryItem;
import com.acme.salary.reference.ReferenceDataResponse.DepartmentItem;
import com.acme.salary.reference.ReferenceDataResponse.FxRateItem;
import com.acme.salary.reference.ReferenceDataResponse.JobLevelItem;
import com.acme.salary.reference.ReferenceDataResponse.JobTitleItem;
import com.acme.salary.salary.SalaryChangeReason;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceDataService {

    private final CountryRepository countries;
    private final DepartmentRepository departments;
    private final JobTitleRepository jobTitles;
    private final FxRateRepository fxRates;

    public ReferenceDataService(CountryRepository countries, DepartmentRepository departments,
                                JobTitleRepository jobTitles, FxRateRepository fxRates) {
        this.countries = countries;
        this.departments = departments;
        this.jobTitles = jobTitles;
        this.fxRates = fxRates;
    }

    @Transactional(readOnly = true)
    public ReferenceDataResponse getAll() {
        return new ReferenceDataResponse(
                countries.findAllByOrderByNameAsc().stream()
                        .map(c -> new CountryItem(c.getCode(), c.getName(), c.getCurrencyCode()))
                        .toList(),
                departments.findAllByOrderByNameAsc().stream()
                        .map(d -> new DepartmentItem(d.getId(), d.getName()))
                        .toList(),
                jobTitles.findAllByOrderByNameAsc().stream()
                        .map(t -> new JobTitleItem(t.getId(), t.getName(), t.getDepartment().getId()))
                        .toList(),
                Arrays.stream(JobLevel.values())
                        .map(l -> new JobLevelItem(l.name(), l.displayName()))
                        .toList(),
                Arrays.stream(SalaryChangeReason.values())
                        .filter(SalaryChangeReason::isUserSelectable)
                        .map(Enum::name)
                        .toList(),
                fxRates.findAllByOrderByCurrencyCodeAsc().stream()
                        .map(r -> new FxRateItem(r.getCurrencyCode(), r.getRateToUsd().toPlainString(), r.getAsOfDate()))
                        .toList());
    }
}
