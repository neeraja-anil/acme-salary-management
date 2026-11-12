package com.acme.salary.seed;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntFunction;

import com.acme.salary.reference.JobLevel;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.seed.SeedEmployee.Salary;
import com.acme.salary.seed.SeedReferenceData.CountryRef;
import com.acme.salary.seed.SeedReferenceData.DepartmentRef;
import com.acme.salary.seed.SeedReferenceData.JobTitleRef;

/**
 * Generates realistic employees with salary histories (US-17). Deterministic: the same reference data,
 * random seed and reference date always produce exactly the same employees, in the same order, so tests
 * and demos can rely on known values. It touches no database and reads no clock.
 *
 * <p>For each employee: a country, department, job title and level drawn from weighted distributions;
 * a hire date up to eight years before the reference date; today's salary from {@link PayModel}; and
 * zero to three later changes (annual reviews, promotions, market adjustments, corrections) roughly a year
 * apart. The starting salary is worked backwards from today's, so current pay always matches the role.
 * About 5% of employees have left.
 */
final class SeedDataGenerator {

    static final String EMAIL_DOMAIN = "acme.example";

    /** Share of employees per country (relative weights). */
    private static final Map<String, Integer> COUNTRY_WEIGHTS = Map.of(
            "US", 30, "IN", 20, "GB", 12, "DE", 10, "CA", 8, "AU", 7, "FR", 7, "SG", 6);

    /** Share of employees per department (relative weights); an unknown department gets 5. */
    private static final Map<String, Integer> DEPARTMENT_WEIGHTS = Map.of(
            "Engineering", 30, "Sales", 14, "Customer Support", 12, "Marketing", 8, "Operations", 8,
            "Product", 7, "Finance", 6, "Design", 5, "Human Resources", 5, "Legal", 5);

    /** People managers and leads: fewer of them, and never below Senior (L3). */
    private static final Set<String> LEADERSHIP_TITLES = Set.of(
            "Engineering Manager", "Sales Manager", "Marketing Manager", "Finance Manager",
            "Support Team Lead", "HR Business Partner");

    private static final JobLevel[] LEVELS = JobLevel.values();
    private static final int[] LEVEL_WEIGHTS = {20, 35, 28, 12, 5};
    private static final JobLevel[] LEADERSHIP_LEVELS = {JobLevel.L3, JobLevel.L4, JobLevel.L5};
    private static final int[] LEADERSHIP_LEVEL_WEIGHTS = {50, 35, 15};

    /** How many salary changes after the starting salary: 0, 1, 2 or 3 (about 2.3 salary rows each). */
    private static final int[] CHANGE_COUNT_WEIGHTS = {25, 35, 25, 15};

    private static final SalaryChangeReason[] CHANGE_REASONS = {
            SalaryChangeReason.ANNUAL_REVIEW, SalaryChangeReason.PROMOTION,
            SalaryChangeReason.MARKET_ADJUSTMENT, SalaryChangeReason.CORRECTION};
    private static final int[] CHANGE_REASON_WEIGHTS = {55, 15, 20, 10};

    private static final int MAX_TENURE_DAYS = 8 * 365;
    private static final int MIN_TENURE_DAYS = 14;
    /** No raise in someone's first six months, and at least six months between changes. */
    private static final int MIN_DAYS_BETWEEN_CHANGES = 180;
    /** Changes land six to about nineteen months after the previous one. */
    private static final int CHANGE_SPREAD_DAYS = 400;
    private static final double TERMINATED_SHARE = 0.05;

    private final SeedReferenceData reference;
    private final LocalDate referenceDate;
    private final Random random;
    private final Map<String, Integer> emailCounts = new HashMap<>();

    SeedDataGenerator(SeedReferenceData reference, long randomSeed, LocalDate referenceDate) {
        if (reference.countries().isEmpty() || reference.departments().isEmpty()) {
            throw new IllegalStateException("Reference data is missing; the V2 migration should have added it");
        }
        this.reference = reference;
        this.referenceDate = referenceDate;
        this.random = new Random(randomSeed);
    }

    /** Employees {@code EMP-000001} to {@code EMP-<count>}, in order. */
    List<SeedEmployee> generate(int count) {
        List<SeedEmployee> employees = new ArrayList<>(count);
        for (int number = 1; number <= count; number++) {
            employees.add(next(number));
        }
        return employees;
    }

    private SeedEmployee next(int number) {
        CountryRef country = pick(reference.countries(), c -> COUNTRY_WEIGHTS.getOrDefault(c.code(), 1));
        DepartmentRef department = pick(reference.departments(), d -> DEPARTMENT_WEIGHTS.getOrDefault(d.name(), 5));
        JobTitleRef jobTitle = pick(reference.jobTitlesIn(department), t -> isLeadership(t) ? 1 : 3);
        JobLevel level = isLeadership(jobTitle)
                ? pick(LEADERSHIP_LEVELS, LEADERSHIP_LEVEL_WEIGHTS)
                : pick(LEVELS, LEVEL_WEIGHTS);

        String[] name = Names.pick(country.code(), random);
        String email = uniqueEmail(name[0], name[1]);

        LocalDate hireDate = referenceDate.minusDays(MIN_TENURE_DAYS + random.nextInt(MAX_TENURE_DAYS));
        long currentSalary = PayModel.salaryFor(country.code(), department.name(), jobTitle.name(), level, random);
        List<Salary> salaries = salaryHistory(hireDate, currentSalary);

        LocalDate terminationDate = null;
        LocalDate lastChange = salaries.get(salaries.size() - 1).effectiveDate();
        if (random.nextDouble() < TERMINATED_SHARE && lastChange.isBefore(referenceDate)) {
            terminationDate = lastChange.plusDays(1 + random.nextInt((int) daysBetween(lastChange, referenceDate)));
        }

        return new SeedEmployee(String.format("EMP-%06d", number), name[0], name[1], email, country, department,
                jobTitle, level, hireDate, terminationDate, salaries);
    }

    /**
     * The starting salary on the hire date, then each change in turn. Change dates are chosen first, then
     * the percentages, and the starting salary is worked backwards so the last row lands on today's pay.
     */
    private List<Salary> salaryHistory(LocalDate hireDate, long currentSalary) {
        List<LocalDate> dates = new ArrayList<>();
        int wanted = pick(new Integer[] {0, 1, 2, 3}, CHANGE_COUNT_WEIGHTS);
        LocalDate previous = hireDate;
        for (int i = 0; i < wanted; i++) {
            LocalDate earliest = previous.plusDays(MIN_DAYS_BETWEEN_CHANGES);
            if (earliest.isAfter(referenceDate)) {
                break;
            }
            int room = (int) Math.min(CHANGE_SPREAD_DAYS, daysBetween(earliest, referenceDate));
            previous = earliest.plusDays(random.nextInt(room + 1));
            dates.add(previous);
        }

        List<SalaryChangeReason> reasons = new ArrayList<>();
        List<Double> rises = new ArrayList<>();
        double growth = 1;
        for (int i = 0; i < dates.size(); i++) {
            SalaryChangeReason reason = pick(CHANGE_REASONS, CHANGE_REASON_WEIGHTS);
            double rise = riseFor(reason);
            reasons.add(reason);
            rises.add(rise);
            growth *= 1 + rise;
        }

        List<Salary> salaries = new ArrayList<>(dates.size() + 1);
        long amount = PayModel.roundToHundred(currentSalary / growth);
        salaries.add(new Salary(amount, null, hireDate, SalaryChangeReason.NEW_HIRE));
        for (int i = 0; i < dates.size(); i++) {
            long next = PayModel.roundToHundred(amount * (1 + rises.get(i)));
            if (next == amount) {
                next = rises.get(i) < 0 && amount > 100 ? amount - 100 : amount + 100; // a change always changes pay
            }
            salaries.add(new Salary(next, amount, dates.get(i), reasons.get(i)));
            amount = next;
        }
        return salaries;
    }

    /** The size of a change, as a fraction (0.04 = +4%). Corrections can go down. */
    private double riseFor(SalaryChangeReason reason) {
        return switch (reason) {
            case ANNUAL_REVIEW -> between(0.02, 0.06);
            case PROMOTION -> between(0.08, 0.15);
            case MARKET_ADJUSTMENT -> between(0.03, 0.08);
            case CORRECTION -> {
                double correction = between(-0.03, 0.03);
                yield Math.abs(correction) < 0.005 ? 0.01 : correction;
            }
            default -> throw new IllegalArgumentException("Not a seeded change reason: " + reason);
        };
    }

    /** {@code first.last@acme.example}, then {@code first.last2@…} and so on for later namesakes. */
    private String uniqueEmail(String firstName, String lastName) {
        String local = (slug(firstName) + "." + slug(lastName));
        int seen = emailCounts.merge(local, 1, Integer::sum);
        return (seen == 1 ? local : local + seen) + "@" + EMAIL_DOMAIN;
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static boolean isLeadership(JobTitleRef jobTitle) {
        return LEADERSHIP_TITLES.contains(jobTitle.name());
    }

    private double between(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private <T> T pick(List<T> options, ToIntFunction<T> weight) {
        if (options.isEmpty()) {
            throw new IllegalStateException("Nothing to choose from; is the reference data complete?");
        }
        int total = options.stream().mapToInt(weight).sum();
        int roll = random.nextInt(total);
        for (T option : options) {
            roll -= weight.applyAsInt(option);
            if (roll < 0) {
                return option;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private <T> T pick(T[] options, int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < options.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return options[i];
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static long daysBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }
}
