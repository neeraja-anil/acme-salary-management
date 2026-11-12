package com.acme.salary.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import com.acme.salary.analytics.dto.RecentChangesResponse;
import com.acme.salary.common.error.InvalidParameterException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Sorting the recent changes list, on seeded data and a fixed "today" (the seed's reference date). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RecentChangesSortTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + tempDir.resolve("sort-test.db") + "?foreign_keys=true&journal_mode=WAL");
        registry.add("app.seed.enabled", () -> "true");
        registry.add("app.seed.employees", () -> 600);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private AnalyticsService analytics;

    @Test
    void defaultIsNewestFirst() {
        List<RecentChangesResponse.Change> changes = page("effectiveDate,desc");
        assertThat(changes).hasSizeGreaterThan(10);
        assertThat(changes).isSortedAccordingTo(
                Comparator.comparing(RecentChangesResponse.Change::effectiveDate).reversed());
    }

    @Test
    void sortsByChangePercentAcrossAllPages() {
        RecentChangesResponse all = analytics.recentChanges(365, 0, 100, "changePercent,desc");
        List<RecentChangesResponse.Change> first = all.changes().content();
        assertThat(first).isSortedAccordingTo(
                Comparator.comparing(RecentChangesResponse.Change::changePercent).reversed());

        // The largest rise on page 1 really is the largest of the period, not just of one page.
        BigDecimal largestOnFirstPage = first.get(0).changePercent();
        int lastPage = all.changes().totalPages() - 1;
        List<RecentChangesResponse.Change> last =
                analytics.recentChanges(365, lastPage, 100, "changePercent,desc").changes().content();
        assertThat(last).allMatch(c -> c.changePercent().compareTo(largestOnFirstPage) <= 0);

        List<RecentChangesResponse.Change> ascending = page("changePercent,asc");
        assertThat(ascending).isSortedAccordingTo(Comparator.comparing(RecentChangesResponse.Change::changePercent));
        assertThat(ascending.get(0).changePercent().signum()).isNegative(); // corrections can lower pay
    }

    @Test
    void rejectsUnknownSorts() {
        assertThatThrownBy(() -> page("amount,desc")).isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("Unknown sort field");
        assertThatThrownBy(() -> page("changePercent,sideways")).isInstanceOf(InvalidParameterException.class);
    }

    private List<RecentChangesResponse.Change> page(String sort) {
        return analytics.recentChanges(365, 0, 100, sort).changes().content();
    }
}
