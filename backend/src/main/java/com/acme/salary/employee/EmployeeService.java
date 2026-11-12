package com.acme.salary.employee;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import com.acme.salary.audit.AuditAction;
import com.acme.salary.audit.AuditChanges;
import com.acme.salary.audit.AuditEntityType;
import com.acme.salary.audit.AuditEvent;
import com.acme.salary.audit.AuditService;
import com.acme.salary.auth.CurrentUser;
import com.acme.salary.common.error.BusinessRuleException;
import com.acme.salary.common.error.ConflictException;
import com.acme.salary.common.error.NotFoundException;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.Employee.JobAssignment;
import com.acme.salary.employee.Employee.PersonalDetails;
import com.acme.salary.employee.dto.CreateEmployeeRequest;
import com.acme.salary.employee.dto.DeactivateEmployeeRequest;
import com.acme.salary.employee.dto.EmployeeDetailResponse;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import com.acme.salary.employee.dto.UpdateEmployeeRequest;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryRepository;
import com.acme.salary.reference.CurrencyConverter;
import com.acme.salary.reference.Department;
import com.acme.salary.reference.DepartmentRepository;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.JobTitle;
import com.acme.salary.reference.JobTitleRepository;
import com.acme.salary.salary.SalaryHistory;
import com.acme.salary.salary.SalaryHistoryRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee use cases (US-01 to US-07). Owns the business rules and transaction boundaries;
 * controllers only translate HTTP.
 */
@Service
public class EmployeeService {

    static final String RULE_HIRE_DATE_IN_FUTURE = "HIRE_DATE_IN_FUTURE";
    static final String RULE_INVALID_REFERENCE = "INVALID_REFERENCE";
    static final String RULE_EMPLOYEE_TERMINATED = "EMPLOYEE_TERMINATED";
    static final String RULE_ALREADY_TERMINATED = "ALREADY_TERMINATED";
    static final String RULE_TERMINATION_BEFORE_HIRE = "TERMINATION_BEFORE_HIRE";
    static final String RULE_TERMINATION_IN_FUTURE = "TERMINATION_DATE_IN_FUTURE";

    private final EmployeeRepository employees;
    private final SalaryHistoryRepository salaryHistory;
    private final CountryRepository countries;
    private final DepartmentRepository departments;
    private final JobTitleRepository jobTitles;
    private final EmployeeCodeGenerator codeGenerator;
    private final EmployeeMapper mapper;
    private final AuditService auditService;
    private final CurrencyConverter currencyConverter;
    private final CurrentUser currentUser;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, SalaryHistoryRepository salaryHistory,
                           CountryRepository countries, DepartmentRepository departments,
                           JobTitleRepository jobTitles, EmployeeCodeGenerator codeGenerator,
                           EmployeeMapper mapper, AuditService auditService, CurrencyConverter currencyConverter,
                           CurrentUser currentUser, Clock clock) {
        this.employees = employees;
        this.salaryHistory = salaryHistory;
        this.countries = countries;
        this.departments = departments;
        this.jobTitles = jobTitles;
        this.codeGenerator = codeGenerator;
        this.mapper = mapper;
        this.auditService = auditService;
        this.currencyConverter = currencyConverter;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeSummaryResponse> search(EmployeeSearchCriteria criteria, int page, int size,
                                                        String sort) {
        EmployeeSort.Parsed parsedSort = EmployeeSort.parse(sort);
        Specification<Employee> specification = EmployeeSpecifications.matching(criteria);
        if (parsedSort.bySalaryUsd()) {
            specification = specification.and(EmployeeSpecifications.orderedBySalaryUsd(
                    parsedSort.salaryUsdDirection(), currencyConverter.ratesToUsd()));
        }
        PageRequest pageRequest = PageRequest.of(page, size, parsedSort.sort());
        return PageResponse.from(employees.findAll(specification, pageRequest), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public EmployeeDetailResponse get(Long id) {
        return mapper.toDetail(load(id));
    }

    /** Creates the employee, their first ({@code NEW_HIRE}) salary row and a CREATE audit entry in one transaction. */
    @Transactional
    public EmployeeDetailResponse create(CreateEmployeeRequest request) {
        if (request.hireDate().isAfter(today())) {
            throw new BusinessRuleException(RULE_HIRE_DATE_IN_FUTURE, "Hire date cannot be in the future");
        }
        Country country = countries.findById(request.countryCode().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> invalidReference("Unknown country '" + request.countryCode() + "'"));
        JobAssignment job = resolveJob(request.departmentId(), request.jobTitleId(), request.jobLevel());
        PersonalDetails personal = normalise(request.firstName(), request.lastName(), request.email());
        if (employees.existsByEmail(personal.email())) {
            throw ConflictException.duplicateEmail(personal.email());
        }

        Instant now = now();
        Employee employee = employees.save(Employee.hire(codeGenerator.next(), personal, country, job,
                request.hireDate(), request.startingSalary(), now));
        salaryHistory.save(SalaryHistory.forNewHire(employee, currentUser.username(), now));
        auditService.record(new AuditEvent(AuditAction.CREATE, AuditEntityType.EMPLOYEE, employee.getId(),
                employee.getId(),
                AuditChanges.create()
                        .set("employeeCode", employee.getEmployeeCode())
                        .set("firstName", employee.getFirstName())
                        .set("lastName", employee.getLastName())
                        .set("email", employee.getEmail())
                        .set("country", country.getCode())
                        .set("department", job.department().getName())
                        .set("jobTitle", job.jobTitle().getName())
                        .set("jobLevel", job.jobLevel())
                        .set("hireDate", employee.getHireDate())
                        .set("salary", employee.getCurrentSalaryAmount())
                        .set("currency", country.getCurrencyCode()),
                now));
        return mapper.toDetail(employee);
    }

    @Transactional
    public EmployeeDetailResponse update(Long id, UpdateEmployeeRequest request) {
        Employee employee = load(id);
        if (employee.isTerminated()) {
            throw new BusinessRuleException(RULE_EMPLOYEE_TERMINATED, "Terminated employees are read-only");
        }
        requireVersion(employee, request.version());
        JobAssignment job = resolveJob(request.departmentId(), request.jobTitleId(), request.jobLevel());
        PersonalDetails personal = normalise(request.firstName(), request.lastName(), request.email());
        if (employees.existsByEmailAndIdNot(personal.email(), id)) {
            throw ConflictException.duplicateEmail(personal.email());
        }

        EmployeeAuditSnapshot before = EmployeeAuditSnapshot.of(employee);
        Instant now = now();
        if (employee.updateDetails(personal, job, now)) {
            auditService.record(new AuditEvent(AuditAction.UPDATE, AuditEntityType.EMPLOYEE, employee.getId(),
                    employee.getId(), before.diff(EmployeeAuditSnapshot.of(employee)), now));
            employees.flush(); // so the response carries the incremented version
        }
        return mapper.toDetail(employee);
    }

    /** Soft delete (US-07): the record and its history are kept, but excluded from analytics. */
    @Transactional
    public EmployeeDetailResponse deactivate(Long id, DeactivateEmployeeRequest request) {
        Employee employee = load(id);
        if (employee.isTerminated()) {
            throw new BusinessRuleException(RULE_ALREADY_TERMINATED, "Employee is already terminated");
        }
        requireVersion(employee, request.version());
        LocalDate terminationDate = request.terminationDate();
        if (terminationDate.isBefore(employee.getHireDate())) {
            throw new BusinessRuleException(RULE_TERMINATION_BEFORE_HIRE,
                    "Termination date cannot be before the hire date (" + employee.getHireDate() + ")");
        }
        if (terminationDate.isAfter(today())) {
            throw new BusinessRuleException(RULE_TERMINATION_IN_FUTURE, "Termination date cannot be in the future");
        }

        Instant now = now();
        employee.terminate(terminationDate, now);
        auditService.record(new AuditEvent(AuditAction.DEACTIVATE, AuditEntityType.EMPLOYEE, employee.getId(),
                employee.getId(),
                AuditChanges.create()
                        .changed("status", EmployeeStatus.ACTIVE, EmployeeStatus.TERMINATED)
                        .set("terminationDate", terminationDate),
                now));
        employees.flush();
        return mapper.toDetail(employee);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Employee load(Long id) {
        return employees.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Employee", id));
    }

    private JobAssignment resolveJob(Long departmentId, Long jobTitleId, JobLevel jobLevel) {
        Department department = departments.findById(departmentId)
                .orElseThrow(() -> invalidReference("Unknown department " + departmentId));
        JobTitle jobTitle = jobTitles.findById(jobTitleId)
                .orElseThrow(() -> invalidReference("Unknown job title " + jobTitleId));
        if (!jobTitle.belongsTo(department)) {
            throw invalidReference("Job title '" + jobTitle.getName() + "' does not belong to department '"
                    + department.getName() + "'");
        }
        return new JobAssignment(department, jobTitle, jobLevel);
    }

    /** The client must send the version it loaded; anything else means someone changed the record since. */
    private static void requireVersion(Employee employee, Long expectedVersion) {
        if (!employee.getVersion().equals(expectedVersion)) {
            throw ConflictException.staleVersion();
        }
    }

    private static PersonalDetails normalise(String firstName, String lastName, String email) {
        return new PersonalDetails(firstName.trim(), lastName.trim(), email.trim().toLowerCase(Locale.ROOT));
    }

    private static BusinessRuleException invalidReference(String detail) {
        return new BusinessRuleException(RULE_INVALID_REFERENCE, detail);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Timestamps are stored with second precision; truncate here so API responses match the database. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }
}
