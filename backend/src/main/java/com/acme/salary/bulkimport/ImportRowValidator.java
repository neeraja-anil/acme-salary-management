package com.acme.salary.bulkimport;

import static com.acme.salary.bulkimport.ImportFormat.ANNUAL_SALARY;
import static com.acme.salary.bulkimport.ImportFormat.COUNTRY_CODE;
import static com.acme.salary.bulkimport.ImportFormat.CURRENCY;
import static com.acme.salary.bulkimport.ImportFormat.DEPARTMENT;
import static com.acme.salary.bulkimport.ImportFormat.EMAIL;
import static com.acme.salary.bulkimport.ImportFormat.FIRST_NAME;
import static com.acme.salary.bulkimport.ImportFormat.HIRE_DATE;
import static com.acme.salary.bulkimport.ImportFormat.JOB_LEVEL;
import static com.acme.salary.bulkimport.ImportFormat.JOB_TITLE;
import static com.acme.salary.bulkimport.ImportFormat.LAST_NAME;
import static com.acme.salary.bulkimport.ImportFormat.SALARY_EFFECTIVE_DATE;
import static com.acme.salary.bulkimport.ImportFormat.SALARY_REASON;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.acme.salary.reference.Country;
import com.acme.salary.reference.Department;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.JobTitle;
import com.acme.salary.salary.SalaryChangeReason;

/**
 * Checks every row of an import file (api-contracts.md section 5a) with the same rules as "Add employee",
 * plus the checks only a file needs: the currency column must match the country, and an email can't appear
 * twice in the file. Every problem in a row is reported, not just the first, so HR can fix a row in one go.
 * Pure: lookups, existing emails and today are passed in, so it is easy to test.
 */
final class ImportRowValidator {

    /** A row that passed every check, with lookups resolved. */
    record ValidRow(int row, String firstName, String lastName, String email, Country country, Department department,
                    JobTitle jobTitle, JobLevel jobLevel, LocalDate hireDate, long salary, LocalDate effectiveDate,
                    SalaryChangeReason reason) {
    }

    record Result(List<ValidRow> valid, int invalidRows, List<ImportReport.RowError> errors) {
    }

    /** The same limits as the API (CreateEmployeeRequest). */
    private static final int MAX_NAME = 100;
    private static final int MAX_EMAIL = 254;
    private static final long MAX_SALARY = 100_000_000;
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,12}");
    /** OTHER isn't accepted: it needs a note, and the file has no note column. */
    private static final Set<SalaryChangeReason> FILE_REASONS = EnumSet.of(SalaryChangeReason.PROMOTION,
            SalaryChangeReason.ANNUAL_REVIEW, SalaryChangeReason.MARKET_ADJUSTMENT, SalaryChangeReason.CORRECTION);

    private final Map<String, Country> countries;
    private final Map<String, Department> departments;
    private final Map<Long, Map<String, JobTitle>> titlesByDepartment;
    private final Set<String> existingEmails;
    private final LocalDate today;

    ImportRowValidator(Collection<Country> countries, Collection<Department> departments,
                       Collection<JobTitle> jobTitles, Set<String> existingEmails, LocalDate today) {
        this.countries = countries.stream().collect(Collectors.toMap(Country::getCode, Function.identity()));
        this.departments = departments.stream().collect(Collectors.toMap(d -> lower(d.getName()), Function.identity()));
        this.titlesByDepartment = jobTitles.stream().collect(Collectors.groupingBy(t -> t.getDepartment().getId(),
                Collectors.toMap(t -> lower(t.getName()), Function.identity())));
        this.existingEmails = existingEmails;
        this.today = today;
    }

    Result validate(List<CsvFileReader.Row> rows) {
        List<ValidRow> valid = new ArrayList<>();
        List<ImportReport.RowError> errors = new ArrayList<>();
        Map<String, Integer> emailFirstSeen = new HashMap<>();
        int invalidRows = 0;
        for (CsvFileReader.Row row : rows) {
            List<ImportReport.RowError> rowErrors = new ArrayList<>();
            ValidRow checked = check(row, rowErrors, emailFirstSeen);
            if (rowErrors.isEmpty()) {
                valid.add(checked);
            } else {
                invalidRows++;
                errors.addAll(rowErrors);
            }
        }
        return new Result(valid, invalidRows, errors);
    }

    private ValidRow check(CsvFileReader.Row row, List<ImportReport.RowError> errors,
                           Map<String, Integer> emailFirstSeen) {
        Problems problems = new Problems(row, errors);

        String firstName = problems.name(FIRST_NAME);
        String lastName = problems.name(LAST_NAME);

        String email = lower(row.get(EMAIL));
        if (email.isEmpty()) {
            problems.add(EMAIL, "required");
        } else if (email.length() > MAX_EMAIL || !EMAIL_FORMAT.matcher(email).matches()) {
            problems.add(EMAIL, "not a valid email address");
        } else if (existingEmails.contains(email)) {
            problems.add(EMAIL, "already used by an existing employee");
        } else {
            Integer firstRow = emailFirstSeen.putIfAbsent(email, row.row());
            if (firstRow != null) {
                problems.add(EMAIL, "duplicate of row " + firstRow);
            }
        }

        String countryCode = row.get(COUNTRY_CODE).toUpperCase(Locale.ROOT);
        Country country = countries.get(countryCode);
        if (countryCode.isEmpty()) {
            problems.add(COUNTRY_CODE, "required");
        } else if (country == null) {
            problems.add(COUNTRY_CODE, "unknown country");
        }

        Department department = departments.get(lower(row.get(DEPARTMENT)));
        if (row.get(DEPARTMENT).isEmpty()) {
            problems.add(DEPARTMENT, "required");
        } else if (department == null) {
            problems.add(DEPARTMENT, "unknown department");
        }

        JobTitle jobTitle = null;
        if (row.get(JOB_TITLE).isEmpty()) {
            problems.add(JOB_TITLE, "required");
        } else if (department != null) {
            jobTitle = titlesByDepartment.getOrDefault(department.getId(), Map.of()).get(lower(row.get(JOB_TITLE)));
            if (jobTitle == null) {
                problems.add(JOB_TITLE, "not a job title in " + department.getName());
            }
        }

        JobLevel jobLevel = null;
        try {
            jobLevel = JobLevel.valueOf(row.get(JOB_LEVEL).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            problems.add(JOB_LEVEL, row.get(JOB_LEVEL).isEmpty() ? "required" : "must be L1, L2, L3, L4 or L5");
        }

        LocalDate hireDate = problems.date(HIRE_DATE, true);
        if (hireDate != null && hireDate.isAfter(today)) {
            problems.add(HIRE_DATE, "can't be in the future");
            hireDate = null;
        }

        Long salary = null;
        String salaryText = row.get(ANNUAL_SALARY);
        if (salaryText.isEmpty()) {
            problems.add(ANNUAL_SALARY, "required");
        } else if (!WHOLE_NUMBER.matcher(salaryText).matches()) {
            problems.add(ANNUAL_SALARY, "must be a whole number with no symbols, like 78000");
        } else {
            salary = Long.parseLong(salaryText);
            if (salary <= 0) {
                problems.add(ANNUAL_SALARY, "must be greater than 0");
            } else if (salary > MAX_SALARY) {
                problems.add(ANNUAL_SALARY, "is too large");
            }
        }

        String currency = row.get(CURRENCY).toUpperCase(Locale.ROOT);
        if (currency.isEmpty()) {
            problems.add(CURRENCY, "required");
        } else if (country != null && !currency.equals(country.getCurrencyCode())) {
            problems.add(CURRENCY, "must be " + country.getCurrencyCode() + " for country " + country.getCode());
        }

        // Optional: when the current salary took effect, and why. Blank means "still the starting salary".
        LocalDate effectiveDate = problems.date(SALARY_EFFECTIVE_DATE, false);
        if (effectiveDate != null && hireDate != null
                && (effectiveDate.isBefore(hireDate) || effectiveDate.isAfter(today))) {
            problems.add(SALARY_EFFECTIVE_DATE, "must be between the hire date and today");
        }
        LocalDate salaryDate = effectiveDate != null ? effectiveDate : hireDate;
        boolean changedSinceHire = hireDate != null && salaryDate != null && salaryDate.isAfter(hireDate);

        SalaryChangeReason reason = null;
        String reasonText = row.get(SALARY_REASON).toUpperCase(Locale.ROOT);
        if (!reasonText.isEmpty()) {
            try {
                reason = SalaryChangeReason.valueOf(reasonText);
            } catch (IllegalArgumentException ex) {
                reason = null;
            }
            if (reason == null || !FILE_REASONS.contains(reason)) {
                problems.add(SALARY_REASON, "must be PROMOTION, ANNUAL_REVIEW, MARKET_ADJUSTMENT or CORRECTION");
            } else if (hireDate != null && !changedSinceHire) {
                problems.add(SALARY_REASON, "only allowed when salary_effective_date is after hire_date");
            }
        }

        if (!problems.isEmpty()) {
            return null;
        }
        // Reason stored on the salary row: NEW_HIRE if still on the starting salary, else the given one or null.
        SalaryChangeReason storedReason = changedSinceHire ? reason : SalaryChangeReason.NEW_HIRE;
        return new ValidRow(row.row(), firstName, lastName, email, country, department, jobTitle, jobLevel, hireDate,
                salary, salaryDate, storedReason);
    }

    private static String lower(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Collects one row's problems, each with the column and the value as it appears in the file. */
    private static final class Problems {
        private final CsvFileReader.Row row;
        private final List<ImportReport.RowError> errors;
        private final int before;

        Problems(CsvFileReader.Row row, List<ImportReport.RowError> errors) {
            this.row = row;
            this.errors = errors;
            this.before = errors.size();
        }

        void add(String column, String message) {
            errors.add(new ImportReport.RowError(row.row(), column, row.get(column), message));
        }

        boolean isEmpty() {
            return errors.size() == before;
        }

        String name(String column) {
            String value = row.get(column);
            if (value.isEmpty()) {
                add(column, "required");
            } else if (value.length() > MAX_NAME) {
                add(column, "must be at most " + MAX_NAME + " characters");
            }
            return value;
        }

        /** An ISO date (YYYY-MM-DD), or null if blank or unreadable (and reported unless optional and blank). */
        LocalDate date(String column, boolean required) {
            String value = row.get(column);
            if (value.isEmpty()) {
                if (required) {
                    add(column, "required");
                }
                return null;
            }
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ex) {
                add(column, "must be a date like 2021-03-15 (YYYY-MM-DD)");
                return null;
            }
        }
    }
}
