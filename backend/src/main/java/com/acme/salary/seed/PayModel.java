package com.acme.salary.seed;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import com.acme.salary.reference.JobLevel;

/**
 * Plausible annual pay for the seed (architecture.md section 7):
 * base for the country × department × job title × level, ±15% jitter, rounded to 100 local units.
 * The figures are illustrative, not market data.
 */
final class PayModel {

    /** A mid-level (L2) individual contributor in a 1.0 department, in local currency. */
    private static final Map<String, Long> BASE_BY_COUNTRY = Map.of(
            "US", 95_000L,
            "GB", 52_000L,
            "DE", 60_000L,
            "FR", 50_000L,
            "IN", 1_400_000L,
            "CA", 82_000L,
            "SG", 85_000L,
            "AU", 100_000L);

    private static final Map<String, Double> BY_DEPARTMENT = Map.of(
            "Engineering", 1.10,
            "Product", 1.10,
            "Design", 1.00,
            "Sales", 0.95,
            "Marketing", 0.92,
            "Customer Support", 0.70,
            "Finance", 1.00,
            "Human Resources", 0.88,
            "Operations", 0.80,
            "Legal", 1.15);

    /** Titles that pay more or less than their department's typical role. Anything else is 1.0. */
    private static final Map<String, Double> BY_JOB_TITLE = Map.ofEntries(
            Map.entry("Engineering Manager", 1.30),
            Map.entry("Data Engineer", 1.05),
            Map.entry("QA Engineer", 0.90),
            Map.entry("Product Analyst", 0.85),
            Map.entry("UX Researcher", 0.95),
            Map.entry("Sales Development Representative", 0.75),
            Map.entry("Sales Manager", 1.25),
            Map.entry("Content Strategist", 0.95),
            Map.entry("Marketing Manager", 1.20),
            Map.entry("Support Team Lead", 1.20),
            Map.entry("Finance Manager", 1.25),
            Map.entry("Accountant", 0.90),
            Map.entry("HR Business Partner", 1.15),
            Map.entry("Recruiter", 0.95),
            Map.entry("Office Manager", 0.85),
            Map.entry("Legal Counsel", 1.25),
            Map.entry("Paralegal", 0.70));

    private static final Map<JobLevel, Double> BY_LEVEL = new EnumMap<>(Map.of(
            JobLevel.L1, 0.72,
            JobLevel.L2, 1.00,
            JobLevel.L3, 1.30,
            JobLevel.L4, 1.60,
            JobLevel.L5, 2.00));

    private static final double JITTER = 0.15;

    private PayModel() {
    }

    /** Today's salary for someone in this role, before history is worked out backwards from it. */
    static long salaryFor(String countryCode, String department, String jobTitle, JobLevel level, Random random) {
        Long base = BASE_BY_COUNTRY.get(countryCode);
        if (base == null) {
            throw new IllegalStateException("No base salary for country " + countryCode + "; add it to PayModel");
        }
        double jitter = 1 + (random.nextDouble() * 2 - 1) * JITTER;
        double amount = base
                * BY_DEPARTMENT.getOrDefault(department, 1.0)
                * BY_JOB_TITLE.getOrDefault(jobTitle, 1.0)
                * BY_LEVEL.get(level)
                * jitter;
        return roundToHundred(amount);
    }

    static long roundToHundred(double amount) {
        return Math.max(100, Math.round(amount / 100) * 100);
    }
}
