package com.acme.salary.employee;

import com.acme.salary.audit.AuditChanges;
import com.acme.salary.reference.JobLevel;

/**
 * The audited, user-editable fields of an employee at one point in time. Department and job title are
 * captured by name: the audit trail is a historical record and should read correctly even if a
 * lookup is renamed later.
 */
record EmployeeAuditSnapshot(String firstName, String lastName, String email, String department,
                             String jobTitle, JobLevel jobLevel) {

    static EmployeeAuditSnapshot of(Employee e) {
        return new EmployeeAuditSnapshot(e.getFirstName(), e.getLastName(), e.getEmail(),
                e.getDepartment().getName(), e.getJobTitle().getName(), e.getJobLevel());
    }

    /** Only the fields that differ between this snapshot and {@code after}. */
    AuditChanges diff(EmployeeAuditSnapshot after) {
        return AuditChanges.create()
                .changed("firstName", firstName, after.firstName)
                .changed("lastName", lastName, after.lastName)
                .changed("email", email, after.email)
                .changed("department", department, after.department)
                .changed("jobTitle", jobTitle, after.jobTitle)
                .changed("jobLevel", jobLevel, after.jobLevel);
    }
}
