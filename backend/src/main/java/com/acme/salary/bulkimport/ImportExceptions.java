package com.acme.salary.bulkimport;

import java.util.Map;

import com.acme.salary.common.error.ApiException;

import org.springframework.http.HttpStatus;

/** The import's errors (api-contracts.md section 2). */
final class ImportExceptions {

    private ImportExceptions() {
    }

    /** Not a readable CSV, or required columns are missing. */
    static final class InvalidFile extends ApiException {
        InvalidFile(String detail) {
            super(HttpStatus.BAD_REQUEST, "INVALID_FILE", "Invalid file", detail);
        }
    }

    /** Over 5 MB or 10,000 rows. */
    static final class FileTooLarge extends ApiException {
        FileTooLarge(String detail) {
            super(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "File too large", detail);
        }
    }

    /** An import was confirmed but some rows are invalid. The body carries the same report as the preview. */
    static final class HasErrors extends ApiException {
        private final ImportReport report;

        HasErrors(ImportReport report) {
            super(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_HAS_ERRORS", "Import has errors",
                    report.invalidRows() + " of " + report.totalRows() + " rows have errors. Nothing was imported.");
            this.report = report;
        }

        @Override
        public Map<String, Object> properties() {
            return Map.of("report", report);
        }
    }
}
