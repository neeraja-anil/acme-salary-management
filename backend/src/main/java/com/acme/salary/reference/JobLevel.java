package com.acme.salary.reference;

/**
 * Seniority, kept separate from the job title so that pay bands can be grouped by title, level or both.
 * "Senior Software Engineer" = Software Engineer + L3.
 */
public enum JobLevel {
    L1("Associate"),
    L2("Mid"),
    L3("Senior"),
    L4("Lead"),
    L5("Principal");

    private final String displayName;

    JobLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
