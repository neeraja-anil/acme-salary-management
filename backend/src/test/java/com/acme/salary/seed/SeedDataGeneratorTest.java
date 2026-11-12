package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.acme.salary.reference.JobLevel;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.seed.SeedEmployee.Salary;
import com.acme.salary.seed.SeedReferenceData.CountryRef;
import com.acme.salary.seed.SeedReferenceData.DepartmentRef;
import com.acme.salary.seed.SeedReferenceData.JobTitleRef;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeedDataGeneratorTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 1);
    private static final int COUNT = 2_000;

    private static List<SeedEmployee> employees;

    @BeforeAll
    static void generate() {
        employees = new SeedDataGenerator(referenceData(), 42, REFERENCE_DATE).generate(COUNT);
    }

    @Test
    void sameSeedProducesIdenticalData() {
        List<SeedEmployee> again = new SeedDataGenerator(referenceData(), 42, REFERENCE_DATE).generate(COUNT);
        assertThat(again).isEqualTo(employees);
    }

    @Test
    void differentSeedProducesDifferentData() {
        List<SeedEmployee> other = new SeedDataGenerator(referenceData(), 7, REFERENCE_DATE).generate(COUNT);
        assertThat(other).isNotEqualTo(employees);
    }

    @Test
    void codesAreSequentialAndEmailsUnique() {
        assertThat(employees).hasSize(COUNT);
        assertThat(employees.get(0).employeeCode()).isEqualTo("EMP-000001");
        assertThat(employees.get(COUNT - 1).employeeCode()).isEqualTo("EMP-002000");
        Set<String> emails = new HashSet<>();
        employees.forEach(e -> assertThat(emails.add(e.email())).as("unique email %s", e.email()).isTrue());
        assertThat(emails).allMatch(email -> email.matches("[a-z.]+\\d*@acme\\.example"));
    }

    @Test
    void jobTitleBelongsToDepartmentAndLeadersAreSenior() {
        for (SeedEmployee e : employees) {
            assertThat(e.jobTitle().departmentId()).isEqualTo(e.department().id());
            if (e.jobTitle().name().endsWith("Manager") && !e.jobTitle().name().equals("Office Manager")
                    && !e.jobTitle().name().equals("Product Manager")) {
                assertThat(e.jobLevel()).isIn(JobLevel.L3, JobLevel.L4, JobLevel.L5);
            }
        }
    }

    @Test
    void salaryHistoryFollowsTheAppsRules() {
        for (SeedEmployee e : employees) {
            List<Salary> salaries = e.salaries();
            assertThat(salaries).hasSizeBetween(1, 4);

            Salary first = salaries.get(0);
            assertThat(first.reason()).isEqualTo(SalaryChangeReason.NEW_HIRE);
            assertThat(first.previousAmount()).isNull();
            assertThat(first.effectiveDate()).isEqualTo(e.hireDate());
            assertThat(e.hireDate()).isBefore(REFERENCE_DATE);

            for (int i = 1; i < salaries.size(); i++) {
                Salary previous = salaries.get(i - 1);
                Salary change = salaries.get(i);
                assertThat(change.previousAmount()).isEqualTo(previous.amount());
                assertThat(change.amount()).isPositive().isNotEqualTo(previous.amount());
                assertThat(change.effectiveDate()).isAfter(previous.effectiveDate()).isBeforeOrEqualTo(REFERENCE_DATE);
                assertThat(change.reason()).isNotIn(SalaryChangeReason.NEW_HIRE, SalaryChangeReason.OTHER);
            }
            assertThat(salaries).allMatch(s -> s.amount() % 100 == 0);
        }
    }

    @Test
    void leaversLeaveAfterTheirLastSalaryChange() {
        List<SeedEmployee> leavers = employees.stream().filter(SeedEmployee::isTerminated).toList();
        assertThat(leavers.size()).isBetween(COUNT * 3 / 100, COUNT * 7 / 100); // about 5%
        for (SeedEmployee e : leavers) {
            assertThat(e.terminationDate()).isAfter(e.currentSalary().effectiveDate())
                    .isBeforeOrEqualTo(REFERENCE_DATE);
        }
    }

    @Test
    void dataIsVariedEnoughForTheScreens() {
        Set<String> countries = new HashSet<>();
        Set<JobLevel> levels = new HashSet<>();
        long withHistory = 0;
        long changedInLast30Days = 0;
        for (SeedEmployee e : employees) {
            countries.add(e.country().code());
            levels.add(e.jobLevel());
            if (e.salaries().size() > 1) {
                withHistory++;
            }
            if (e.salaries().stream().skip(1)
                    .anyMatch(s -> !s.effectiveDate().isBefore(REFERENCE_DATE.minusDays(30)))) {
                changedInLast30Days++;
            }
        }
        assertThat(countries).hasSize(8);
        assertThat(levels).containsExactlyInAnyOrder(JobLevel.values());
        assertThat(withHistory).isGreaterThan(COUNT / 2); // "some employees have more than one salary"
        assertThat(changedInLast30Days).isPositive();      // the dashboard's "last 30 days" has something to show
    }

    @Test
    void payIsPlausibleForTheCountry() {
        // An Indian salary is in rupees, so it is far larger in number than a US one in dollars.
        double medianUs = medianCurrentSalary("US");
        double medianIn = medianCurrentSalary("IN");
        assertThat(medianUs).isBetween(60_000.0, 160_000.0);
        assertThat(medianIn).isBetween(800_000.0, 2_500_000.0);
    }

    @Test
    void sampleCsvFieldsAreQuotedOnlyWhenNeeded() {
        assertThat(SampleCsvWriter.escape("Lena")).isEqualTo("Lena");
        assertThat(SampleCsvWriter.escape("Smith, Jr")).isEqualTo("\"Smith, Jr\"");
        assertThat(SampleCsvWriter.escape("O\"Neil")).isEqualTo("\"O\"\"Neil\"");
    }

    private static double medianCurrentSalary(String countryCode) {
        List<Long> amounts = new ArrayList<>(employees.stream()
                .filter(e -> e.country().code().equals(countryCode))
                .map(e -> e.currentSalary().amount())
                .sorted()
                .toList());
        return amounts.get(amounts.size() / 2);
    }

    /** The same lookups as the V2 migration. */
    static SeedReferenceData referenceData() {
        List<CountryRef> countries = List.of(new CountryRef("AU", "AUD"), new CountryRef("CA", "CAD"),
                new CountryRef("DE", "EUR"), new CountryRef("FR", "EUR"), new CountryRef("GB", "GBP"),
                new CountryRef("IN", "INR"), new CountryRef("SG", "SGD"), new CountryRef("US", "USD"));
        String[] departmentNames = {"Engineering", "Product", "Design", "Sales", "Marketing", "Customer Support",
                "Finance", "Human Resources", "Operations", "Legal"};
        List<DepartmentRef> departments = new ArrayList<>();
        for (int i = 0; i < departmentNames.length; i++) {
            departments.add(new DepartmentRef(i + 1, departmentNames[i]));
        }
        String[][] titles = {
                {"1", "Software Engineer"}, {"1", "QA Engineer"}, {"1", "DevOps Engineer"}, {"1", "Data Engineer"},
                {"1", "Engineering Manager"}, {"2", "Product Manager"}, {"2", "Product Analyst"},
                {"3", "Product Designer"}, {"3", "UX Researcher"}, {"4", "Account Executive"},
                {"4", "Sales Development Representative"}, {"4", "Sales Manager"}, {"5", "Marketing Specialist"},
                {"5", "Content Strategist"}, {"5", "Marketing Manager"}, {"6", "Support Specialist"},
                {"6", "Support Team Lead"}, {"7", "Financial Analyst"}, {"7", "Accountant"},
                {"7", "Finance Manager"}, {"8", "HR Generalist"}, {"8", "Recruiter"}, {"8", "HR Business Partner"},
                {"9", "Operations Analyst"}, {"9", "Office Manager"}, {"10", "Legal Counsel"}, {"10", "Paralegal"}};
        List<JobTitleRef> jobTitles = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            jobTitles.add(new JobTitleRef(i + 1, titles[i][1], Long.parseLong(titles[i][0])));
        }
        return new SeedReferenceData(countries, departments, jobTitles);
    }
}
