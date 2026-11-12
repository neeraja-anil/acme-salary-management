package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Seeds a fresh SQLite file through the real migrations, so every row has to pass the schema's
 * constraints and triggers, then checks the data hangs together the way the app expects.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseSeederTest {

    private static final int EMPLOYEES = 400;
    private static final int CSV_ROWS = 30;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + tempDir.resolve("seed-test.db") + "?foreign_keys=true&journal_mode=WAL");
        registry.add("app.seed.enabled", () -> "true");
        registry.add("app.seed.employees", () -> EMPLOYEES);
        registry.add("app.seed.sample-csv", () -> tempDir.resolve("samples/employees-sample.csv").toString());
        registry.add("app.seed.sample-csv-rows", () -> CSV_ROWS);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DatabaseSeeder seeder;

    @Test
    void seedsEmployeesWithConsistentHistoryAndAudit() {
        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(EMPLOYEES);
        assertThat(count("SELECT MAX(employee_code) FROM employee", String.class)).isEqualTo("EMP-000400");

        // The denormalised current salary matches the latest history row for every employee.
        assertThat(count("""
                SELECT COUNT(*) FROM employee e
                WHERE e.current_salary_amount <> (SELECT h.amount FROM salary_history h WHERE h.employee_id = e.id
                                                  ORDER BY h.effective_date DESC, h.id DESC LIMIT 1)
                   OR e.current_salary_effective_date <> (SELECT MAX(h.effective_date) FROM salary_history h
                                                          WHERE h.employee_id = e.id)""")).isZero();

        // Salary history is in the employee's currency.
        assertThat(count("""
                SELECT COUNT(*) FROM salary_history h JOIN employee e ON e.id = h.employee_id
                JOIN country c ON c.code = e.country_code WHERE h.currency_code <> c.currency_code""")).isZero();

        // One CREATE per employee, one SALARY_CHANGE per later salary, one DEACTIVATE per leaver.
        int salaries = count("SELECT COUNT(*) FROM salary_history");
        int leavers = count("SELECT COUNT(*) FROM employee WHERE status = 'TERMINATED'");
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE'")).isEqualTo(EMPLOYEES);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'SALARY_CHANGE'"))
                .isEqualTo(salaries - EMPLOYEES);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'DEACTIVATE'")).isEqualTo(leavers);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE performed_by <> 'system-seed'")).isZero();
    }

    @Test
    void auditEntriesUseTheAppsChangeFormat() {
        Map<String, Object> create = jdbc.queryForMap(
                "SELECT changes, performed_at FROM audit_log WHERE action = 'CREATE' ORDER BY id LIMIT 1");
        assertThat((String) create.get("changes"))
                .startsWith("{\"employeeCode\":{\"to\":\"EMP-000001\"}")
                .contains("\"jobLevel\":{\"to\":\"L")
                .containsPattern("\"hireDate\":\\{\"to\":\"\\d{4}-\\d{2}-\\d{2}\"}");
        assertThat((String) create.get("performed_at")).matches("\\d{4}-\\d{2}-\\d{2}T09:00:00Z");

        String salaryChange = jdbc.queryForObject(
                "SELECT changes FROM audit_log WHERE action = 'SALARY_CHANGE' ORDER BY id LIMIT 1", String.class);
        assertThat(salaryChange).containsPattern("^\\{\"amount\":\\{\"from\":\\d+,\"to\":\\d+}");
    }

    @Test
    void runningAgainAddsNothing() throws Exception {
        seeder.run(new DefaultApplicationArguments());
        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(EMPLOYEES);
    }

    @Test
    void writesASampleCsvInTheImportFormatWithNewPeople() throws IOException {
        List<String> lines = Files.readAllLines(tempDir.resolve("samples/employees-sample.csv"),
                StandardCharsets.UTF_8);
        assertThat(lines.get(0)).isEqualTo(String.join(",", SampleCsvWriter.HEADER));
        assertThat(lines.size() - 1).isBetween(CSV_ROWS * 8 / 10, CSV_ROWS); // leavers are skipped

        List<String> seededEmails = jdbc.queryForList("SELECT email FROM employee", String.class);
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split(",", -1);
            assertThat(fields).hasSize(SampleCsvWriter.HEADER.size());
            assertThat(seededEmails).doesNotContain(fields[2]); // imports into a seeded database without clashes
        }
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private <T> T count(String sql, Class<T> type) {
        return jdbc.queryForObject(sql, type);
    }
}
