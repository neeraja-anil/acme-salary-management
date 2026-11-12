package com.acme.salary.bulkimport;

import java.time.LocalDate;
import java.util.List;

import com.acme.salary.reference.JobLevel;

/**
 * What a check of the file found (api-contracts.md section 5a). {@code row} is the line number in the
 * file with the header as line 1, so it matches what HR sees in Excel.
 */
public record ImportReport(String fileName, int totalRows, int validRows, int invalidRows, List<RowError> errors,
                           boolean errorsTruncated, List<SampleRow> sample) {

    public record RowError(int row, String column, String value, String message) {
    }

    /** One of the first valid rows, so HR can check the columns were read correctly. */
    public record SampleRow(int row, String firstName, String lastName, String email, String countryCode,
                            String department, String jobTitle, JobLevel jobLevel, LocalDate hireDate,
                            long annualSalary, String currency) {
    }
}
