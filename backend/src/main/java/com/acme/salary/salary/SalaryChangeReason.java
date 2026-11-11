package com.acme.salary.salary;

/** Why a salary row exists. {@code NEW_HIRE} and {@code IMPORT} are set by the system only. */
public enum SalaryChangeReason {
    NEW_HIRE(false),
    IMPORT(false),
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
