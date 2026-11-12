package com.acme.salary.bulkimport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.acme.salary.audit.AuditAction;
import com.acme.salary.audit.AuditChanges;
import com.acme.salary.audit.AuditEntityType;
import com.acme.salary.employee.EmployeeStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Inserts validated rows with JDBC batch inserts: 10,000 employees take a second or two, where JPA would
 * insert row by row. Each employee gets the next employee code, a first salary row, and an IMPORT audit
 * entry by the signed-in user, built with the same {@link AuditChanges} format as "Add employee".
 * Runs inside the caller's transaction, so the import is all or nothing.
 */
@Component
class ImportWriter {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    ImportWriter(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    int write(List<ImportRowValidator.ValidRow> rows, String username, Instant now) {
        long employeeId = maxLong("SELECT COALESCE(MAX(id), 0) FROM employee");
        long salaryId = maxLong("SELECT COALESCE(MAX(id), 0) FROM salary_history");
        long codeNumber = maxLong("SELECT COALESCE(MAX(CAST(SUBSTR(employee_code, 5) AS INTEGER)), 0) FROM employee");
        String timestamp = now.toString();

        List<Object[]> employees = new ArrayList<>(rows.size());
        List<Object[]> salaries = new ArrayList<>(rows.size());
        List<Object[]> audits = new ArrayList<>(rows.size());
        for (ImportRowValidator.ValidRow r : rows) {
            employeeId++;
            salaryId++;
            String code = String.format("EMP-%06d", ++codeNumber);
            String currency = r.country().getCurrencyCode();

            employees.add(new Object[] {employeeId, code, r.firstName(), r.lastName(), r.email(), r.country().getCode(),
                    r.department().getId(), r.jobTitle().getId(), r.jobLevel().name(), r.hireDate().toString(),
                    EmployeeStatus.ACTIVE.name(), r.salary(), r.effectiveDate().toString(), timestamp, timestamp});
            salaries.add(new Object[] {salaryId, employeeId, r.salary(), currency, r.effectiveDate().toString(),
                    r.reason() == null ? null : r.reason().name(), username, timestamp});

            AuditChanges changes = AuditChanges.create()
                    .set("employeeCode", code)
                    .set("firstName", r.firstName())
                    .set("lastName", r.lastName())
                    .set("email", r.email())
                    .set("country", r.country().getCode())
                    .set("department", r.department().getName())
                    .set("jobTitle", r.jobTitle().getName())
                    .set("jobLevel", r.jobLevel())
                    .set("hireDate", r.hireDate())
                    .set("salary", r.salary())
                    .set("currency", currency);
            if (!r.effectiveDate().equals(r.hireDate())) {
                changes.set("effectiveDate", r.effectiveDate()).set("reason", r.reason());
            }
            audits.add(new Object[] {AuditEntityType.EMPLOYEE.name(), employeeId, employeeId, AuditAction.IMPORT.name(),
                    jsonMapper.writeValueAsString(changes.asMap()), username, timestamp});
        }

        jdbc.batchUpdate("""
                INSERT INTO employee (id, employee_code, first_name, last_name, email, country_code, department_id,
                                      job_title_id, job_level, hire_date, status, termination_date,
                                      current_salary_amount, current_salary_effective_date, version,
                                      created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, 0, ?, ?)""", employees);
        // previous_amount is always NULL: history starts at the import, so there is no earlier salary.
        jdbc.batchUpdate("""
                INSERT INTO salary_history (id, employee_id, amount, currency_code, previous_amount, effective_date,
                                            reason, note, changed_by, changed_at)
                VALUES (?, ?, ?, ?, NULL, ?, ?, NULL, ?, ?)""", salaries);
        jdbc.batchUpdate("""
                INSERT INTO audit_log (entity_type, entity_id, employee_id, action, changes, performed_by,
                                       performed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", audits);
        return employees.size();
    }

    private long maxLong(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
