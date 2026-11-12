package com.acme.salary.audit;

import com.acme.salary.common.error.NotFoundException;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** An employee's audit trail (US-10, api-contracts.md section 7). */
@RestController
public class AuditController {

    private final AuditService auditService;
    private final EmployeeRepository employees;

    public AuditController(AuditService auditService, EmployeeRepository employees) {
        this.auditService = auditService;
        this.employees = employees;
    }

    @GetMapping("/api/employees/{id}/audit")
    public PageResponse<AuditEntryResponse> auditTrail(
            @PathVariable Long id,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        if (!employees.existsById(id)) {
            throw new NotFoundException("Employee", id);
        }
        return auditService.findForEmployee(id, page, size);
    }
}
