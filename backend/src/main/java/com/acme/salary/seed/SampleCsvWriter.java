package com.acme.salary.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.seed.SeedEmployee.Salary;

/**
 * Writes employees as a CSV in the HR import format (api-contracts.md section 5a), so the import can be
 * demonstrated and tested with a realistic file. One row per employee with their current salary; the file
 * has no history, just like HR's real spreadsheets.
 */
final class SampleCsvWriter {

    static final List<String> HEADER = List.of("first_name", "last_name", "email", "country_code", "department",
            "job_title", "job_level", "hire_date", "annual_salary", "currency", "salary_effective_date",
            "salary_reason");

    /** The reasons the import accepts in the file. NEW_HIRE is implied by a blank reason. */
    private static final Set<SalaryChangeReason> FILE_REASONS = EnumSet.of(SalaryChangeReason.PROMOTION,
            SalaryChangeReason.ANNUAL_REVIEW, SalaryChangeReason.MARKET_ADJUSTMENT, SalaryChangeReason.CORRECTION);

    private SampleCsvWriter() {
    }

    /** Leavers are skipped: an import only adds active employees. Returns the number of rows written. */
    static int write(Path file, List<SeedEmployee> employees) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            int rows = 0;
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                line(out, HEADER);
                for (SeedEmployee e : employees) {
                    if (!e.isTerminated()) {
                        line(out, row(e));
                        rows++;
                    }
                }
            }
            return rows;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write the sample CSV to " + file, ex);
        }
    }

    private static List<String> row(SeedEmployee e) {
        Salary current = e.currentSalary();
        boolean stillOnStartingSalary = current.effectiveDate().equals(e.hireDate());
        String reason = !stillOnStartingSalary && FILE_REASONS.contains(current.reason()) ? current.reason().name() : "";
        return List.of(e.firstName(), e.lastName(), e.email(), e.country().code(), e.department().name(),
                e.jobTitle().name(), e.jobLevel().name(), e.hireDate().toString(), Long.toString(current.amount()),
                e.country().currencyCode(), stillOnStartingSalary ? "" : current.effectiveDate().toString(), reason);
    }

    /** RFC 4180: CRLF line endings; a field with a comma, quote or line break is quoted, quotes doubled. */
    private static void line(Writer out, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(escape(fields.get(i)));
        }
        out.write("\r\n");
    }

    static String escape(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }
}
