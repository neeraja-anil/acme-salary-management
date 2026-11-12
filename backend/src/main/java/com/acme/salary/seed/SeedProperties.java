package com.acme.salary.seed;

import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the demo data seed ({@code app.seed.*}, see application.yml).
 *
 * @param enabled        run the seed at start-up (it still only runs when the employee table is empty)
 * @param employees      how many employees to insert
 * @param randomSeed     fixed seed, so every run produces identical data
 * @param referenceDate  the "today" the data is generated around (no {@code now()}, for the same reason)
 * @param sampleCsv      where to write a sample import file; blank means don't write one
 * @param sampleCsvRows  how many rows the sample file has (extra people, not copies of seeded ones)
 */
@ConfigurationProperties("app.seed")
public record SeedProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("10000") int employees,
        @DefaultValue("42") long randomSeed,
        @DefaultValue("2026-11-01") LocalDate referenceDate,
        String sampleCsv,
        @DefaultValue("200") int sampleCsvRows) {

    boolean writesSampleCsv() {
        return sampleCsv != null && !sampleCsv.isBlank() && sampleCsvRows > 0;
    }
}
