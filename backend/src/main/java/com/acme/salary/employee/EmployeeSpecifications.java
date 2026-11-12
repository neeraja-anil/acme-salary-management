package com.acme.salary.employee;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.acme.salary.reference.Country;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Translates {@link EmployeeSearchCriteria} into a JPA query. */
final class EmployeeSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private EmployeeSpecifications() {
    }

    static Specification<Employee> matching(EmployeeSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.status() != StatusFilter.ALL) {
                predicates.add(cb.equal(root.get("status"), EmployeeStatus.valueOf(criteria.status().name())));
            }
            if (!criteria.countryCodes().isEmpty()) {
                predicates.add(root.get("country").get("code").in(criteria.countryCodes()));
            }
            if (!criteria.departmentIds().isEmpty()) {
                predicates.add(root.get("department").get("id").in(criteria.departmentIds()));
            }
            if (!criteria.jobTitleIds().isEmpty()) {
                predicates.add(root.get("jobTitle").get("id").in(criteria.jobTitleIds()));
            }
            if (!criteria.jobLevels().isEmpty()) {
                predicates.add(root.get("jobLevel").in(criteria.jobLevels()));
            }
            if (criteria.query() != null && !criteria.query().isBlank()) {
                predicates.add(textSearch(root, cb, criteria.query()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Orders by salary converted to USD: {@code current_salary_amount × rate}, where the rate is chosen by the
     * country's currency with a CASE expression built from the fixed rates (7 currencies). Then by id, so
     * paging stays stable. Adds no filter. The count query that paging runs is left unordered.
     */
    static Specification<Employee> orderedBySalaryUsd(Sort.Direction direction, Map<String, BigDecimal> ratesToUsd) {
        return (root, query, cb) -> {
            if (query.getResultType() == Long.class || query.getResultType() == long.class) {
                return null; // count query: ordering is irrelevant
            }
            CriteriaBuilder.SimpleCase<String, BigDecimal> rate =
                    cb.selectCase(root.<Country>get("country").<String>get("currencyCode"));
            ratesToUsd.forEach(rate::when);
            Expression<Number> salaryUsd = cb.prod(root.<Long>get("currentSalaryAmount"), rate.otherwise(BigDecimal.ONE));
            query.orderBy(direction.isAscending() ? cb.asc(salaryUsd) : cb.desc(salaryUsd), cb.asc(root.get("id")));
            return null;
        };
    }

    /** Case-insensitive partial match on first, last or full name, employee code, or email. */
    private static Predicate textSearch(Root<Employee> root, CriteriaBuilder cb, String rawQuery) {
        String pattern = "%" + escapeLike(rawQuery.trim().toLowerCase(Locale.ROOT)) + "%";
        Expression<String> firstName = cb.lower(root.get("firstName"));
        Expression<String> lastName = cb.lower(root.get("lastName"));
        Expression<String> fullName = cb.concat(cb.concat(firstName, " "), lastName);
        return cb.or(
                cb.like(fullName, pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("employeeCode")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE));
    }

    /** Treat user-typed {@code %} and {@code _} literally instead of as wildcards. */
    static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
