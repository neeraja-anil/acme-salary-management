package com.acme.salary.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.acme.salary.audit.AuditAction;
import com.acme.salary.audit.AuditChanges;
import com.acme.salary.audit.AuditEntityType;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.seed.SeedEmployee.Salary;
import com.acme.salary.seed.SeedReferenceData.CountryRef;
import com.acme.salary.seed.SeedReferenceData.DepartmentRef;
import com.acme.salary.seed.SeedReferenceData.JobTitleRef;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes generated employees with JDBC batch inserts, all in one transaction: either the whole seed is
 * there or none of it. JPA would insert row by row, which is far slower for ~60,000 rows.
 *
 * <p>Rows look exactly like ones the app writes itself: the same timestamp format, the denormalised
 * current salary, and audit entries built with {@link AuditChanges} and the app's JSON mapper
 * (a CREATE per employee, a SALARY_CHANGE per later salary, a DEACTIVATE for leavers), all by
 * {@value #SEED_USER}.
 */
@Component
class SeedWriter {

    static final String SEED_USER = "system-seed";

    /** Seeded events are stamped at 09:00 UTC on their date. */
    private static final LocalTime EVENT_TIME = LocalTime.of(9, 0);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper jsonMapper;

    SeedWriter(JdbcTemplate jdbc, TransactionTemplate transaction, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.jsonMapper = jsonMapper;
    }

    /** Lookups in a fixed order (codes and ids), so the generator always sees them the same way. */
    SeedReferenceData loadReferenceData() {
        return new SeedReferenceData(
                jdbc.query("SELECT code, currency_code FROM country ORDER BY code",
                        (rs, i) -> new CountryRef(rs.getString(1), rs.getString(2))),
                jdbc.query("SELECT id, name FROM department ORDER BY id",
                        (rs, i) -> new DepartmentRef(rs.getLong(1), rs.getString(2))),
                jdbc.query("SELECT id, name, department_id FROM job_title ORDER BY id",
                        (rs, i) -> new JobTitleRef(rs.getLong(1), rs.getString(2), rs.getLong(3))));
    }

    boolean hasEmployees() {
        Integer count = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM employee)", Integer.class);
        return count != null && count == 1;
    }

    /** Inserts everything and returns how many rows went into each table. */
    Counts write(List<SeedEmployee> employees) {
        return transaction.execute(status -> insert(employees));
    }

    record Counts(int employees, int salaries, int auditEntries) {
    }

    private Counts insert(List<SeedEmployee> employees) {
        // Ids are assigned here, so salary and audit rows can point at them without reading anything back.
        long employeeId = maxId("employee");
        long salaryId = maxId("salary_history");

        List<Object[]> employeeRows = new ArrayList<>(employees.size());
        List<Object[]> salaryRows = new ArrayList<>();
        List<Object[]> auditRows = new ArrayList<>();

        for (SeedEmployee e : employees) {
            employeeId++;
            Salary current = e.currentSalary();
            Instant createdAt = at(e.hireDate());
            Instant updatedAt = at(e.isTerminated() ? e.terminationDate() : current.effectiveDate());
            employeeRows.add(new Object[] {
                    employeeId, e.employeeCode(), e.firstName(), e.lastName(), e.email(), e.country().code(),
                    e.department().id(), e.jobTitle().id(), e.jobLevel().name(), e.hireDate().toString(),
                    (e.isTerminated() ? EmployeeStatus.TERMINATED : EmployeeStatus.ACTIVE).name(),
                    e.isTerminated() ? e.terminationDate().toString() : null,
                    current.amount(), current.effectiveDate().toString(), createdAt.toString(),
                    updatedAt.toString()});

            auditRows.add(audit(AuditEntityType.EMPLOYEE, employeeId, employeeId, AuditAction.CREATE,
                    AuditChanges.create()
                            .set("employeeCode", e.employeeCode())
                            .set("firstName", e.firstName())
                            .set("lastName", e.lastName())
                            .set("email", e.email())
                            .set("country", e.country().code())
                            .set("department", e.department().name())
                            .set("jobTitle", e.jobTitle().name())
                            .set("jobLevel", e.jobLevel())
                            .set("hireDate", e.hireDate())
                            .set("salary", e.salaries().get(0).amount())
                            .set("currency", e.country().currencyCode()),
                    createdAt));

            Salary previous = null;
            for (Salary s : e.salaries()) {
                salaryId++;
                Instant changedAt = at(s.effectiveDate());
                salaryRows.add(new Object[] {
                        salaryId, employeeId, s.amount(), e.country().currencyCode(), s.previousAmount(),
                        s.effectiveDate().toString(), s.reason().name(), SEED_USER, changedAt.toString()});
                if (previous != null) {
                    auditRows.add(audit(AuditEntityType.SALARY, salaryId, employeeId, AuditAction.SALARY_CHANGE,
                            AuditChanges.create()
                                    .changed("amount", previous.amount(), s.amount())
                                    .changed("effectiveDate", previous.effectiveDate(), s.effectiveDate())
                                    .set("currency", e.country().currencyCode())
                                    .set("reason", s.reason()),
                            changedAt));
                }
                previous = s;
            }

            if (e.isTerminated()) {
                auditRows.add(audit(AuditEntityType.EMPLOYEE, employeeId, employeeId, AuditAction.DEACTIVATE,
                        AuditChanges.create()
                                .changed("status", EmployeeStatus.ACTIVE, EmployeeStatus.TERMINATED)
                                .set("terminationDate", e.terminationDate()),
                        updatedAt));
            }
        }

        jdbc.batchUpdate("""
                INSERT INTO employee (id, employee_code, first_name, last_name, email, country_code, department_id,
                                      job_title_id, job_level, hire_date, status, termination_date,
                                      current_salary_amount, current_salary_effective_date, version,
                                      created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)""", employeeRows);
        jdbc.batchUpdate("""
                INSERT INTO salary_history (id, employee_id, amount, currency_code, previous_amount, effective_date,
                                            reason, note, changed_by, changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)""", salaryRows);
        jdbc.batchUpdate("""
                INSERT INTO audit_log (entity_type, entity_id, employee_id, action, changes, performed_by,
                                       performed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", auditRows);

        return new Counts(employeeRows.size(), salaryRows.size(), auditRows.size());
    }

    private Object[] audit(AuditEntityType entityType, long entityId, long employeeId, AuditAction action,
                           AuditChanges changes, Instant at) {
        return new Object[] {entityType.name(), entityId, employeeId, action.name(),
                jsonMapper.writeValueAsString(changes.asMap()), SEED_USER, at.toString()};
    }

    private long maxId(String table) {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return max == null ? 0 : max;
    }

    private static Instant at(LocalDate date) {
        return date.atTime(EVENT_TIME).toInstant(ZoneOffset.UTC);
    }
}
