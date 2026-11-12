package com.acme.salary.bulkimport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.reference.CountryRepository;
import com.acme.salary.reference.DepartmentRepository;
import com.acme.salary.reference.JobTitleRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV import (US-21). Stateless: the preview saves nothing, and the import checks the same file again
 * before saving, so nothing can change between the two and there are no temporary files.
 */
@Service
public class ImportService {

    public record ImportResult(String fileName, int importedCount) {
    }

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final CountryRepository countries;
    private final DepartmentRepository departments;
    private final JobTitleRepository jobTitles;
    private final JdbcTemplate jdbc;
    private final ImportWriter writer;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ImportService(CountryRepository countries, DepartmentRepository departments, JobTitleRepository jobTitles,
                         JdbcTemplate jdbc, ImportWriter writer, CurrentUser currentUser, Clock clock) {
        this.countries = countries;
        this.departments = departments;
        this.jobTitles = jobTitles;
        this.jdbc = jdbc;
        this.writer = writer;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** The header row plus one example row, with a byte-order mark so Excel opens it as UTF-8. */
    public byte[] template() {
        String csv = "﻿" + String.join(",", ImportFormat.ALL) + "\r\n" + String.join(",", ImportFormat.EXAMPLE) + "\r\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    /** Checks every row and saves nothing. */
    @Transactional(readOnly = true)
    public ImportReport preview(MultipartFile file) {
        return check(file).report();
    }

    /** Imports every row in one transaction, or nothing if any row is invalid. */
    @Transactional
    public ImportResult importFile(MultipartFile file) {
        Checked checked = check(file);
        if (checked.report().invalidRows() > 0) {
            throw new ImportExceptions.HasErrors(checked.report());
        }
        String username = currentUser.username();
        int imported = writer.write(checked.valid(), username, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        log.info("{} imported {} employees from {}", username, imported, checked.report().fileName());
        return new ImportResult(checked.report().fileName(), imported);
    }

    private record Checked(ImportReport report, List<ImportRowValidator.ValidRow> valid) {
    }

    private Checked check(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImportExceptions.InvalidFile("Choose a CSV file to upload");
        }
        if (file.getSize() > ImportFormat.MAX_FILE_BYTES) {
            throw new ImportExceptions.FileTooLarge("The file is over 5 MB. Split it into smaller files.");
        }
        List<CsvFileReader.Row> rows = CsvFileReader.read(bytes(file));

        // Lookups and existing emails are loaded once for the whole file: one query each.
        Set<String> existingEmails = new HashSet<>();
        jdbc.query("SELECT email FROM employee", rs -> {
            existingEmails.add(rs.getString(1).toLowerCase(Locale.ROOT));
        });
        ImportRowValidator.Result result = new ImportRowValidator(countries.findAll(), departments.findAll(),
                jobTitles.findAll(), existingEmails, LocalDate.now(clock)).validate(rows);

        List<ImportReport.RowError> errors = result.errors();
        boolean truncated = errors.size() > ImportFormat.MAX_ERRORS_LISTED;
        List<ImportReport.SampleRow> sample = result.valid().stream()
                .limit(ImportFormat.SAMPLE_ROWS)
                .map(r -> new ImportReport.SampleRow(r.row(), r.firstName(), r.lastName(), r.email(),
                        r.country().getCode(), r.department().getName(), r.jobTitle().getName(), r.jobLevel(),
                        r.hireDate(), r.salary(), r.country().getCurrencyCode()))
                .toList();
        ImportReport report = new ImportReport(fileName(file), rows.size(), result.valid().size(),
                result.invalidRows(), truncated ? errors.subList(0, ImportFormat.MAX_ERRORS_LISTED) : errors,
                truncated, sample);
        return new Checked(report, result.valid());
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the uploaded file", ex);
        }
    }

    private static String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "upload.csv" : name;
    }
}
