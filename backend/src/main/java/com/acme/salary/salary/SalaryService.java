package com.acme.salary.salary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.acme.salary.audit.AuditAction;
import com.acme.salary.audit.AuditChanges;
import com.acme.salary.audit.AuditEntityType;
import com.acme.salary.audit.AuditEvent;
import com.acme.salary.audit.AuditService;
import com.acme.salary.auth.CurrentUser;
import com.acme.salary.common.error.BusinessRuleException;
import com.acme.salary.common.error.ConflictException;
import com.acme.salary.common.error.NotFoundException;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.salary.dto.SalaryChangeRequest;
import com.acme.salary.salary.dto.SalaryHistoryEntryResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Salary changes and history (US-08, US-09). */
@Service
public class SalaryService {

    static final String RULE_EMPLOYEE_TERMINATED = "EMPLOYEE_TERMINATED";

    private final EmployeeRepository employees;
    private final SalaryHistoryRepository salaryHistory;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public SalaryService(EmployeeRepository employees, SalaryHistoryRepository salaryHistory,
                         AuditService auditService, CurrentUser currentUser, Clock clock) {
        this.employees = employees;
        this.salaryHistory = salaryHistory;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SalaryHistoryEntryResponse> history(Long employeeId) {
        if (!employees.existsById(employeeId)) {
            throw new NotFoundException("Employee", employeeId);
        }
        return salaryHistory.findByEmployeeIdOrderByEffectiveDateDescIdDesc(employeeId).stream()
                .map(SalaryService::toResponse)
                .toList();
    }

    /**
     * Records a salary change. In one transaction: inserts the history row, updates the employee's
     * denormalised current salary (bumping its version), and writes the audit entry. If any step fails,
     * none of them is saved.
     */
    @Transactional
    public SalaryHistoryEntryResponse change(Long employeeId, SalaryChangeRequest request) {
        Employee employee = employees.findWithDetailsById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));
        if (employee.isTerminated()) {
            throw new BusinessRuleException(RULE_EMPLOYEE_TERMINATED,
                    "Salary cannot be changed for a terminated employee");
        }
        if (!employee.getVersion().equals(request.version())) {
            throw ConflictException.staleVersion();
        }
        SalaryChangePolicy.validate(employee.getCurrentSalaryAmount(), employee.getCurrentSalaryEffectiveDate(),
                request.amount(), request.effectiveDate(), LocalDate.now(clock));

        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Long previousAmount = employee.getCurrentSalaryAmount();
        LocalDate previousEffectiveDate = employee.getCurrentSalaryEffectiveDate();

        SalaryHistory entry = salaryHistory.save(SalaryHistory.forChange(employee, request.amount(),
                request.effectiveDate(), request.reason(), request.note(), currentUser.username(), now));
        employee.applySalaryChange(request.amount(), request.effectiveDate(), now);

        auditService.record(new AuditEvent(AuditAction.SALARY_CHANGE, AuditEntityType.SALARY, entry.getId(),
                employee.getId(),
                AuditChanges.create()
                        .changed("amount", previousAmount, entry.getAmount())
                        .changed("effectiveDate", previousEffectiveDate, entry.getEffectiveDate())
                        .set("currency", entry.getCurrencyCode())
                        .set("reason", entry.getReason())
                        .set("note", entry.getNote()),
                now));

        employees.flush(); // surface an optimistic-lock conflict here, inside the transaction
        return toResponse(entry);
    }

    private static SalaryHistoryEntryResponse toResponse(SalaryHistory h) {
        return new SalaryHistoryEntryResponse(
                h.getId(),
                h.getAmount(),
                h.getCurrencyCode(),
                h.getPreviousAmount(),
                SalaryMath.changePercent(h.getPreviousAmount(), h.getAmount()),
                h.getEffectiveDate(),
                h.getReason(),
                h.getNote(),
                h.getChangedBy(),
                h.getChangedAt());
    }
}
