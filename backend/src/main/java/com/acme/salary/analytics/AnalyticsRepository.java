package com.acme.salary.analytics;

import java.time.LocalDate;
import java.util.List;

import com.acme.salary.employee.Employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries for analytics. Returns projections, never entities. */
public interface AnalyticsRepository extends Repository<Employee, Long> {

    /** Current salary of every active employee (terminated employees are excluded from analytics). */
    @Query("""
            select new com.acme.salary.analytics.SalaryPoint(
                e.country.code, e.country.currencyCode, e.department.id, e.jobTitle.id, e.jobLevel,
                e.currentSalaryAmount)
            from Employee e
            where e.status = com.acme.salary.employee.EmployeeStatus.ACTIVE
            """)
    List<SalaryPoint> findActiveSalaryPoints();

    /**
     * Salary changes with an effective date in [from, to], newest first. Rows without a previous amount
     * are an employee's first salary (hire or import), which is a starting point rather than a change.
     */
    @Query(value = """
            select new com.acme.salary.analytics.RecentChangeRow(
                e.id, e.employeeCode, e.firstName, e.lastName, e.country.code, h.currencyCode,
                h.previousAmount, h.amount, h.reason, h.effectiveDate, h.changedBy, h.changedAt)
            from SalaryHistory h join h.employee e
            where h.previousAmount is not null and h.effectiveDate between :from and :to
            order by h.effectiveDate desc, h.id desc
            """,
            countQuery = """
            select count(h) from SalaryHistory h
            where h.previousAmount is not null and h.effectiveDate between :from and :to
            """)
    Page<RecentChangeRow> findRecentChanges(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                            Pageable pageable);

    /** Count and average % change per reason, for the same rows as {@link #findRecentChanges}. */
    @Query("""
            select new com.acme.salary.analytics.ReasonSummaryRow(
                h.reason, count(h), avg((h.amount - h.previousAmount) * 100.0 / h.previousAmount))
            from SalaryHistory h
            where h.previousAmount is not null and h.effectiveDate between :from and :to
            group by h.reason
            """)
    List<ReasonSummaryRow> summariseRecentChangesByReason(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
