package com.acme.salary.salary;

/**
 * Why a salary is the amount it is. {@code NEW_HIRE} is set by the system only (the first salary of an
 * employee added in the app, or an imported salary that is still the starting salary).
 * <p>
 * How a row got into the system (entered in the app or imported) is not a reason; the audit log records
 * that ({@code CREATE} vs {@code IMPORT}).
 */
public enum SalaryChangeReason {
    NEW_HIRE(false),
    PROMOTION(true),
    ANNUAL_REVIEW(true),
    MARKET_ADJUSTMENT(true),
    CORRECTION(true),
    OTHER(true);

    private final boolean userSelectable;

    SalaryChangeReason(boolean userSelectable) {
        this.userSelectable = userSelectable;
    }

    public boolean isUserSelectable() {
        return userSelectable;
    }
}
