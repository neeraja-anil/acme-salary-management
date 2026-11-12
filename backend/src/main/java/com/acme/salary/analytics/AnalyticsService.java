package com.acme.salary.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import com.acme.salary.analytics.dto.CountryPayRow;
import com.acme.salary.analytics.dto.DepartmentPayResponse;
import com.acme.salary.analytics.dto.NamedCountry;
import com.acme.salary.analytics.dto.OverviewResponse;
import com.acme.salary.analytics.dto.PayBandsResponse;
import com.acme.salary.analytics.dto.RecentChangesResponse;
import com.acme.salary.common.error.InvalidParameterException;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.dto.DepartmentRef;
import com.acme.salary.employee.dto.JobTitleRef;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryRepository;
import com.acme.salary.reference.CurrencyConverter;
import com.acme.salary.reference.DepartmentRepository;
import com.acme.salary.reference.FxRate;
import com.acme.salary.reference.FxRateRepository;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.JobTitle;
import com.acme.salary.reference.JobTitleRepository;
import com.acme.salary.salary.SalaryMath;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pay analytics (US-11 to US-15).
 * <p>
 * Approach: load the current salary of every active employee as a small projection (10k rows), then group
 * and compute statistics in Java. One code path computes every statistic, including the median and
 * percentiles that SQLite cannot.
 * <p>
 * Currency rule: a group within one country is reported in that country's currency; a group that spans
 * countries is converted to USD per employee <em>before</em> computing statistics, so currencies are never mixed.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    static final int LOW_SAMPLE_THRESHOLD = 3;
    private static final String USD = "USD";

    private final AnalyticsRepository analytics;
    private final CountryRepository countries;
    private final DepartmentRepository departments;
    private final JobTitleRepository jobTitles;
    private final FxRateRepository fxRates;
    private final CurrencyConverter converter;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository analytics, CountryRepository countries,
                            DepartmentRepository departments, JobTitleRepository jobTitles,
                            FxRateRepository fxRates, CurrencyConverter converter, Clock clock) {
        this.analytics = analytics;
        this.countries = countries;
        this.departments = departments;
        this.jobTitles = jobTitles;
        this.fxRates = fxRates;
        this.converter = converter;
        this.clock = clock;
    }

    /** US-11: org-wide headcount and pay, in USD. */
    public OverviewResponse overview() {
        List<SalaryPoint> points = analytics.findActiveSalaryPoints();
        long[] usdAmounts = points.stream().mapToLong(this::toUsd).toArray();
        PayStatistics stats = usdAmounts.length == 0 ? null : PayStatistics.of(usdAmounts);

        Map<String, Long> byCountry = points.stream()
                .collect(Collectors.groupingBy(SalaryPoint::countryCode, Collectors.counting()));
        Map<Long, Long> byDepartment = points.stream()
                .collect(Collectors.groupingBy(SalaryPoint::departmentId, Collectors.counting()));

        List<OverviewResponse.CountryHeadcount> countryHeadcounts = countries.findAllByOrderByNameAsc().stream()
                .filter(c -> byCountry.containsKey(c.getCode()))
                .map(c -> new OverviewResponse.CountryHeadcount(c.getCode(), c.getName(), byCountry.get(c.getCode())))
                .sorted(Comparator.comparingLong(OverviewResponse.CountryHeadcount::headcount).reversed())
                .toList();
        List<OverviewResponse.DepartmentHeadcount> departmentHeadcounts = departments.findAllByOrderByNameAsc().stream()
                .filter(d -> byDepartment.containsKey(d.getId()))
                .map(d -> new OverviewResponse.DepartmentHeadcount(d.getId(), d.getName(), byDepartment.get(d.getId())))
                .sorted(Comparator.comparingLong(OverviewResponse.DepartmentHeadcount::headcount).reversed())
                .toList();

        LocalDate fxRatesAsOf = fxRates.findAll().stream()
                .map(FxRate::getAsOfDate)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new OverviewResponse(USD, points.size(), stats == null ? 0 : stats.total(),
                stats == null ? null : stats.average(), stats == null ? null : stats.median(),
                countryHeadcounts, departmentHeadcounts, fxRatesAsOf);
    }

    /** US-12: one row per country with employees, in local currency plus the USD equivalent. */
    public List<CountryPayRow> byCountry() {
        Map<String, long[]> amountsByCountry = group(analytics.findActiveSalaryPoints(), SalaryPoint::countryCode,
                SalaryPoint::amount);
        return countries.findAllByOrderByNameAsc().stream()
                .filter(c -> amountsByCountry.containsKey(c.getCode()))
                .map(c -> {
                    PayStatistics local = PayStatistics.of(amountsByCountry.get(c.getCode()));
                    PayStatistics usd = local.convert(amount -> converter.toUsd(amount, c.getCurrencyCode()));
                    return new CountryPayRow(new NamedCountry(c.getCode(), c.getName()), c.getCurrencyCode(), local, usd);
                })
                .toList();
    }

    /** US-13: one row per department with employees, optionally limited to one country. */
    public DepartmentPayResponse byDepartment(String countryCode) {
        Scope scope = scope(countryCode);
        List<SalaryPoint> points = analytics.findActiveSalaryPoints().stream().filter(scope::includes).toList();
        Map<Long, long[]> amountsByDepartment = group(points, SalaryPoint::departmentId, scope.amount());

        List<DepartmentPayResponse.Row> rows = departments.findAllByOrderByNameAsc().stream()
                .filter(d -> amountsByDepartment.containsKey(d.getId()))
                .map(d -> new DepartmentPayResponse.Row(new DepartmentRef(d.getId(), d.getName()),
                        PayStatistics.of(amountsByDepartment.get(d.getId()))))
                .toList();
        return new DepartmentPayResponse(scope.currency(), scope.countryCode(), rows);
    }

    /** US-14: one row per job title and level, optionally filtered by department, title, level and country. */
    public PayBandsResponse payBands(Long departmentId, Long jobTitleId, JobLevel jobLevel, String countryCode) {
        Scope scope = scope(countryCode);
        if (departmentId != null && !departments.existsById(departmentId)) {
            throw new InvalidParameterException("Unknown department id: " + departmentId);
        }
        Map<Long, JobTitle> titlesById = jobTitles.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(JobTitle::getId, Function.identity()));
        if (jobTitleId != null && !titlesById.containsKey(jobTitleId)) {
            throw new InvalidParameterException("Unknown job title id: " + jobTitleId);
        }

        List<SalaryPoint> points = analytics.findActiveSalaryPoints().stream()
                .filter(scope::includes)
                .filter(p -> departmentId == null || p.departmentId().equals(departmentId))
                .filter(p -> jobTitleId == null || p.jobTitleId().equals(jobTitleId))
                .filter(p -> jobLevel == null || p.jobLevel() == jobLevel)
                .toList();
        Map<BandKey, long[]> amountsByBand = group(points, p -> new BandKey(p.jobTitleId(), p.jobLevel()),
                scope.amount());

        List<PayBandsResponse.Row> rows = amountsByBand.entrySet().stream()
                .map(entry -> {
                    JobTitle title = titlesById.get(entry.getKey().jobTitleId());
                    PayStatistics stats = PayStatistics.of(entry.getValue());
                    return new PayBandsResponse.Row(new JobTitleRef(title.getId(), title.getName()),
                            entry.getKey().jobLevel(), stats, stats.headcount() < LOW_SAMPLE_THRESHOLD);
                })
                .sorted(Comparator.comparing((PayBandsResponse.Row r) -> r.jobTitle().name(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PayBandsResponse.Row::jobLevel))
                .toList();
        return new PayBandsResponse(scope.currency(), scope.countryCode(), rows);
    }

    /**
     * US-15: salary changes with an effective date in the last {@code days} days, including today.
     * {@code sort} is {@code effectiveDate} or {@code changePercent}, with {@code asc} or {@code desc}.
     */
    public RecentChangesResponse recentChanges(int days, int page, int size, String sort) {
        Sort order = RecentChangesSort.parse(sort);
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(days - 1L);

        List<ReasonSummaryRow> byReason = analytics.summariseRecentChangesByReason(from, to).stream()
                .sorted(Comparator.comparingLong(ReasonSummaryRow::count).reversed()
                        .thenComparing(ReasonSummaryRow::reason))
                .toList();
        long changeCount = byReason.stream().mapToLong(ReasonSummaryRow::count).sum();
        // Each group's average × its count is that group's sum, so this is the exact overall average.
        Double overallAverage = changeCount == 0 ? null
                : byReason.stream().mapToDouble(r -> r.averageChangePercent() * r.count()).sum() / changeCount;

        RecentChangesResponse.Summary summary = new RecentChangesResponse.Summary(changeCount,
                roundPercent(overallAverage),
                byReason.stream()
                        .map(r -> new RecentChangesResponse.ReasonSummary(r.reason(), r.count(),
                                roundPercent(r.averageChangePercent())))
                        .toList());

        PageResponse<RecentChangesResponse.Change> changes = PageResponse.from(
                analytics.findRecentChanges(from, to, PageRequest.of(page, size, order)), AnalyticsService::toChange);
        return new RecentChangesResponse(days, from, to, summary, changes);
    }

    private static RecentChangesResponse.Change toChange(RecentChangeRow row) {
        return new RecentChangesResponse.Change(row.employeeId(), row.employeeCode(),
                row.firstName() + " " + row.lastName(), row.countryCode(), row.currency(), row.previousAmount(),
                row.amount(), SalaryMath.changePercent(row.previousAmount(), row.amount()), row.reason(),
                row.effectiveDate(), row.changedBy(), row.changedAt());
    }

    private static BigDecimal roundPercent(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private long toUsd(SalaryPoint point) {
        return converter.toUsd(point.amount(), point.currency());
    }

    /**
     * Which employees a breakdown covers and which currency it reports in: one country in its own
     * currency, or all countries converted to USD.
     */
    private Scope scope(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return new Scope(null, USD, this::toUsd);
        }
        String code = countryCode.trim().toUpperCase(Locale.ROOT);
        Country country = countries.findById(code)
                .orElseThrow(() -> new InvalidParameterException("Unknown country: " + code));
        return new Scope(country.getCode(), country.getCurrencyCode(), SalaryPoint::amount);
    }

    private record Scope(String countryCode, String currency, ToLongFunction<SalaryPoint> amount) {
        boolean includes(SalaryPoint point) {
            return countryCode == null || countryCode.equals(point.countryCode());
        }
    }

    private record BandKey(Long jobTitleId, JobLevel jobLevel) {
    }

    private static <K> Map<K, long[]> group(List<SalaryPoint> points, Function<SalaryPoint, K> key,
                                            ToLongFunction<SalaryPoint> amount) {
        return points.stream().collect(Collectors.groupingBy(key,
                Collectors.collectingAndThen(Collectors.toList(),
                        group -> group.stream().mapToLong(amount).toArray())));
    }
}
