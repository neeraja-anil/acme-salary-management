package com.acme.salary.seed;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Fills an empty database with demo data at start-up (US-17), when {@code app.seed.enabled=true}.
 * Idempotent: if any employee exists the database is left alone, so restarting never duplicates data.
 * To start over, delete the SQLite file and restart.
 *
 * <p>Optionally also writes a sample import CSV ({@code app.seed.sample-csv}). Its rows are extra people
 * generated after the seeded ones, so the file imports cleanly into a seeded database (no duplicate emails).
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@EnableConfigurationProperties(SeedProperties.class)
class DatabaseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final SeedProperties properties;
    private final SeedWriter writer;
    private final Clock clock;

    DatabaseSeeder(SeedProperties properties, SeedWriter writer, Clock clock) {
        this.properties = properties;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean seedDatabase = !writer.hasEmployees();
        if (!seedDatabase && !properties.writesSampleCsv()) {
            log.info("Seed skipped: the database already has employees");
            return;
        }

        long started = System.nanoTime();
        int csvRows = properties.writesSampleCsv() ? properties.sampleCsvRows() : 0;
        SeedDataGenerator generator = new SeedDataGenerator(writer.loadReferenceData(), properties.randomSeed(),
                referenceDate());
        List<SeedEmployee> generated = generator.generate(properties.employees() + csvRows);

        if (seedDatabase) {
            SeedWriter.Counts counts = writer.write(generated.subList(0, properties.employees()));
            log.info("Seeded {} employees, {} salary rows and {} audit entries in {} ms", counts.employees(),
                    counts.salaries(), counts.auditEntries(), (System.nanoTime() - started) / 1_000_000);
        } else {
            log.info("Seed skipped: the database already has employees");
        }

        if (csvRows > 0) {
            Path file = Path.of(properties.sampleCsv());
            int written = SampleCsvWriter.write(file, generated.subList(properties.employees(), generated.size()));
            log.info("Wrote {} sample import rows to {}", written, file.toAbsolutePath());
        }
    }

    /**
     * The configured reference date keeps the data identical on every run. It can't be later than today,
     * though, or hire dates and salaries would be in the future, which the app's own rules forbid.
     */
    private LocalDate referenceDate() {
        LocalDate today = LocalDate.now(clock);
        if (properties.referenceDate().isAfter(today)) {
            log.warn("app.seed.reference-date {} is in the future; using today ({}) instead",
                    properties.referenceDate(), today);
            return today;
        }
        return properties.referenceDate();
    }
}
