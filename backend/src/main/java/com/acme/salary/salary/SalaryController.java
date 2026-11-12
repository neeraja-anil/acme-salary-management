package com.acme.salary.salary;

import java.util.List;

import com.acme.salary.salary.dto.SalaryChangeRequest;
import com.acme.salary.salary.dto.SalaryHistoryEntryResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Salary history and changes for one employee (api-contracts.md section 6). */
@RestController
@RequestMapping("/api/employees/{employeeId}/salaries")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @GetMapping
    public List<SalaryHistoryEntryResponse> history(@PathVariable Long employeeId) {
        return salaryService.history(employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryHistoryEntryResponse change(@PathVariable Long employeeId,
                                             @Valid @RequestBody SalaryChangeRequest request) {
        return salaryService.change(employeeId, request);
    }
}
