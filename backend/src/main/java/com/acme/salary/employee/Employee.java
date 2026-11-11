package com.acme.salary.employee;

import java.time.Instant;
import java.time.LocalDate;

import com.acme.salary.reference.Country;
import com.acme.salary.reference.Department;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.JobTitle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * An employee. State changes go through intention-revealing methods ({@link #hire}, {@link #updateDetails},
 * {@link #terminate}) rather than setters, so invariants live in one place:
 * <ul>
 *   <li>country is fixed after creation (it sets the salary currency);</li>
 *   <li>terminated employees are read-only;</li>
 *   <li>salary is never set directly here except through salary changes (current salary is denormalised
 *       from {@code salary_history}).</li>
 * </ul>
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true, updatable = false)
    private String employeeCode;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code", nullable = false, updatable = false)
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_title_id", nullable = false)
    private JobTitle jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_level", nullable = false)
    private JobLevel jobLevel;

    @Column(name = "hire_date", nullable = false, updatable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmployeeStatus status;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "current_salary_amount", nullable = false)
    private Long currentSalaryAmount;

    @Column(name = "current_salary_effective_date", nullable = false)
    private LocalDate currentSalaryEffectiveDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    /** Creates an active employee whose first salary is effective from the hire date. */
    public static Employee hire(String employeeCode, PersonalDetails personal, Country country, JobAssignment job,
                                LocalDate hireDate, long startingSalary, Instant now) {
        Employee employee = new Employee();
        employee.employeeCode = employeeCode;
        employee.applyPersonal(personal);
        employee.country = country;
        employee.applyJob(job);
        employee.hireDate = hireDate;
        employee.status = EmployeeStatus.ACTIVE;
        employee.currentSalaryAmount = startingSalary;
        employee.currentSalaryEffectiveDate = hireDate;
        employee.createdAt = now;
        employee.updatedAt = now;
        return employee;
    }

    /**
     * Applies new personal and job details.
     *
     * @return {@code false} if nothing changed, in which case the record (and its version) is left untouched
     */
    public boolean updateDetails(PersonalDetails personal, JobAssignment job, Instant now) {
        boolean unchanged = personal.equals(new PersonalDetails(firstName, lastName, email))
                && job.department().getId().equals(department.getId())
                && job.jobTitle().getId().equals(jobTitle.getId())
                && job.jobLevel() == jobLevel;
        if (unchanged) {
            return false;
        }
        applyPersonal(personal);
        applyJob(job);
        updatedAt = now;
        return true;
    }

    public void terminate(LocalDate terminationDate, Instant now) {
        this.status = EmployeeStatus.TERMINATED;
        this.terminationDate = terminationDate;
        this.updatedAt = now;
    }

    public boolean isTerminated() {
        return status == EmployeeStatus.TERMINATED;
    }

    private void applyPersonal(PersonalDetails personal) {
        this.firstName = personal.firstName();
        this.lastName = personal.lastName();
        this.email = personal.email();
    }

    private void applyJob(JobAssignment job) {
        this.department = job.department();
        this.jobTitle = job.jobTitle();
        this.jobLevel = job.jobLevel();
    }

    /** Name and contact details; values are expected to be normalised (trimmed, lower-case email). */
    public record PersonalDetails(String firstName, String lastName, String email) {
    }

    /** Department, title and level; the title is expected to belong to the department. */
    public record JobAssignment(Department department, JobTitle jobTitle, JobLevel jobLevel) {
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public Country getCountry() {
        return country;
    }

    public Department getDepartment() {
        return department;
    }

    public JobTitle getJobTitle() {
        return jobTitle;
    }

    public JobLevel getJobLevel() {
        return jobLevel;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public Long getCurrentSalaryAmount() {
        return currentSalaryAmount;
    }

    public LocalDate getCurrentSalaryEffectiveDate() {
        return currentSalaryEffectiveDate;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
