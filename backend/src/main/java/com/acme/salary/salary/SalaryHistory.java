package com.acme.salary.salary;

import java.time.Instant;
import java.time.LocalDate;

import com.acme.salary.employee.Employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * One salary for one employee from an effective date. Rows are append-only: Hibernate never
 * updates them ({@link Immutable}) and database triggers reject UPDATE/DELETE as a second guard.
 */
@Entity
@Immutable
@Table(name = "salary_history")
public class SalaryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "previous_amount")
    private Long previousAmount;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** Null only on a first row whose reason was not recorded (an imported salary); see V1 schema. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    private SalaryChangeReason reason;

    @Column(name = "note")
    private String note;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected SalaryHistory() {
    }

    private SalaryHistory(Employee employee, long amount, String currencyCode, Long previousAmount,
                          LocalDate effectiveDate, SalaryChangeReason reason, String note,
                          String changedBy, Instant changedAt) {
        this.employee = employee;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.previousAmount = previousAmount;
        this.effectiveDate = effectiveDate;
        this.reason = reason;
        this.note = note;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    /** The first salary of a newly hired employee, effective from their hire date. */
    public static SalaryHistory forNewHire(Employee employee, String changedBy, Instant changedAt) {
        return new SalaryHistory(employee, employee.getCurrentSalaryAmount(), employee.getCountry().getCurrencyCode(),
                null, employee.getHireDate(), SalaryChangeReason.NEW_HIRE, null, changedBy, changedAt);
    }

    /**
     * A change from the employee's current salary. Must be called before the employee's current salary
     * is updated, so that {@code previous_amount} captures the salary being replaced.
     */
    public static SalaryHistory forChange(Employee employee, long newAmount, LocalDate effectiveDate,
                                          SalaryChangeReason reason, String note, String changedBy,
                                          Instant changedAt) {
        String trimmedNote = note == null || note.isBlank() ? null : note.trim();
        return new SalaryHistory(employee, newAmount, employee.getCountry().getCurrencyCode(),
                employee.getCurrentSalaryAmount(), effectiveDate, reason, trimmedNote, changedBy, changedAt);
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Long getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Long getPreviousAmount() {
        return previousAmount;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public SalaryChangeReason getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
