package com.acme.salary.analytics;

import java.util.List;

import com.acme.salary.analytics.dto.CountryPayRow;
import com.acme.salary.analytics.dto.DepartmentPayResponse;
import com.acme.salary.analytics.dto.OverviewResponse;
import com.acme.salary.analytics.dto.PayBandsResponse;
import com.acme.salary.analytics.dto.RecentChangesResponse;
import com.acme.salary.reference.JobLevel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pay analytics (api-contracts.md section 8). Active employees only. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        return analyticsService.overview();
    }

    @GetMapping("/by-country")
    public List<CountryPayRow> byCountry() {
        return analyticsService.byCountry();
    }

    @GetMapping("/by-department")
    public DepartmentPayResponse byDepartment(@RequestParam(name = "country", required = false) String country) {
        return analyticsService.byDepartment(country);
    }

    @GetMapping("/pay-bands")
    public PayBandsResponse payBands(@RequestParam(name = "departmentId", required = false) Long departmentId,
                                     @RequestParam(name = "jobTitleId", required = false) Long jobTitleId,
                                     @RequestParam(name = "jobLevel", required = false) JobLevel jobLevel,
                                     @RequestParam(name = "country", required = false) String country) {
        return analyticsService.payBands(departmentId, jobTitleId, jobLevel, country);
    }

    @GetMapping("/recent-changes")
    public RecentChangesResponse recentChanges(
            @RequestParam(name = "days", defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "25") @Min(1) @Max(100) int size) {
        return analyticsService.recentChanges(days, page, size);
    }
}
