package com.acme.salary.employee;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.dto.CreateEmployeeRequest;
import com.acme.salary.employee.dto.DeactivateEmployeeRequest;
import com.acme.salary.employee.dto.EmployeeDetailResponse;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import com.acme.salary.employee.dto.UpdateEmployeeRequest;
import com.acme.salary.reference.JobLevel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Employee directory and lifecycle endpoints (api-contracts.md section 5). */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public PageResponse<EmployeeSummaryResponse> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "country", required = false) List<String> countryCodes,
            @RequestParam(name = "departmentId", required = false) List<Long> departmentIds,
            @RequestParam(name = "jobTitleId", required = false) List<Long> jobTitleIds,
            @RequestParam(name = "jobLevel", required = false) List<JobLevel> jobLevels,
            @RequestParam(name = "status", defaultValue = "ACTIVE") StatusFilter status,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(name = "sort", defaultValue = "name,asc") String sort) {
        List<String> normalisedCountries = countryCodes == null ? List.of()
                : countryCodes.stream().map(c -> c.trim().toUpperCase(Locale.ROOT)).toList();
        EmployeeSearchCriteria criteria = new EmployeeSearchCriteria(
                query, normalisedCountries, departmentIds, jobTitleIds, jobLevels, status);
        return employeeService.search(criteria, page, size, sort);
    }

    @GetMapping("/{id}")
    public EmployeeDetailResponse get(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @PostMapping
    public ResponseEntity<EmployeeDetailResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeDetailResponse created = employeeService.create(request);
        return ResponseEntity.created(URI.create("/api/employees/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public EmployeeDetailResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public EmployeeDetailResponse deactivate(@PathVariable Long id,
                                             @Valid @RequestBody DeactivateEmployeeRequest request) {
        return employeeService.deactivate(id, request);
    }
}
