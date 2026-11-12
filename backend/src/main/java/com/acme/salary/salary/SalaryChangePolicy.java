package com.acme.salary.salary;

import java.time.LocalDate;

import com.acme.salary.common.error.BusinessRuleException;

/**
 * Business rules for changing a salary (US-08), kept free of Spring and persistence so they can be
 * unit-tested directly. Request-shape rules (amount range, allowed reason, note for OTHER) are
 * enforced earlier by bean validation.
 */
public final class SalaryChangePolicy {

    public static final String RULE_EFFECTIVE_DATE_IN_FUTURE = "EFFECTIVE_DATE_IN_FUTURE";
    public static final String RULE_EFFECTIVE_DATE_BEFORE_CURRENT = "EFFECTIVE_DATE_BEFORE_CURRENT";
    public static final String RULE_NO_CHANGE = "NO_CHANGE";

    private SalaryChangePolicy() {
    }

    /**
     * @param currentAmount        the salary in force today
     * @param currentEffectiveDate when the current salary took effect
     * @param newAmount            proposed annual amount
     * @param newEffectiveDate     proposed effective date
     * @param today                today's date (from the injected clock)
     * @throws BusinessRuleException if the change is not allowed
     */
    public static void validate(long currentAmount, LocalDate currentEffectiveDate,
                                long newAmount, LocalDate newEffectiveDate, LocalDate today) {
        // No future-dated changes in v1: the latest row must always be the salary in force today,
        // which keeps employee.current_salary_* correct without a scheduled job.
        if (newEffectiveDate.isAfter(today)) {
            throw new BusinessRuleException(RULE_EFFECTIVE_DATE_IN_FUTURE,
                    "Effective date cannot be in the future");
        }
        // History must stay in date order, so a change cannot take effect before the current salary did.
        if (newEffectiveDate.isBefore(currentEffectiveDate)) {
            throw new BusinessRuleException(RULE_EFFECTIVE_DATE_BEFORE_CURRENT,
                    "Effective date cannot be before the current salary's effective date (" + currentEffectiveDate + ")");
        }
        if (newAmount == currentAmount) {
            throw new BusinessRuleException(RULE_NO_CHANGE, "New salary is the same as the current salary");
        }
    }
}
