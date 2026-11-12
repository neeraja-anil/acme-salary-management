package com.acme.salary.bulkimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** CSV import (US-21) through the HTTP API, signed in, on a seeded SQLite file. */
@SpringBootTest
@AutoConfigureMockMvc
class ImportTest {

    private static final String ADMIN_PASSWORD = "admin-test-password";
    private static final String HEADER = String.join(",", ImportFormat.ALL);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + tempDir.resolve("import-test.db") + "?foreign_keys=true&journal_mode=WAL");
        registry.add("app.admin.password", () -> ADMIN_PASSWORD);
        registry.add("app.seed.enabled", () -> "true");
        registry.add("app.seed.employees", () -> 200);
        registry.add("app.seed.sample-csv", () -> tempDir.resolve("sample.csv").toString());
        registry.add("app.seed.sample-csv-rows", () -> 40);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private MockHttpSession session;

    @BeforeEach
    void signIn() throws Exception {
        session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "admin", "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void importNeedsASignedInUser() throws Exception {
        mvc.perform(multipart("/api/employees/import/preview").file(csv("x.csv", HEADER)).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void templateDownloadsAndItsExampleRowIsValid() throws Exception {
        byte[] template = mvc.perform(get("/api/employees/import/template").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", containsString("employees-import-template.csv")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(template, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿" + HEADER);

        preview(new MockMultipartFile("file", "template.csv", "text/csv", template))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.invalidRows").value(0));
    }

    @Test
    void previewReportsEveryProblemByFileRowAndSavesNothing() throws Exception {
        String existingEmail = jdbc.queryForObject("SELECT email FROM employee ORDER BY id LIMIT 1", String.class);
        int employeesBefore = count("employee");
        String file = String.join("\n", HEADER,
                "Ana,Silva,ana.silva@example.com,DE,Engineering,Software Engineer,L2,2022-01-10,60000,EUR,,",
                "Ben,Cole,ana.silva@example.com,DX,Sales,Software Engineer,L9,2099-01-01,60k,USD,,",
                "\"Smith, Jr\",Ray,smith.jr@example.com,US,Sales,Account Executive,L3,2023-05-01,90000,USD,2023-01-01,",
                "Cara,Diaz," + existingEmail + ",FR,Legal,Paralegal,L1,2024-02-02,40000,USD,,",
                "Dan,Eze,dan.eze@example.com,GB,Finance,Accountant,L2,2024-02-02,50000,GBP,2024-02-02,ANNUAL_REVIEW",
                "",
                "Eve,Fox,eve.fox@example.com,IN,Product,Product Manager,L4,2020-06-01,2500000,INR,2024-04-01,PROMOTION");

        JsonNode report = body(preview(csv("acme.csv", file)).andExpect(status().isOk()));

        assertThat(report.get("fileName").asString()).isEqualTo("acme.csv");
        assertThat(report.get("totalRows").asInt()).isEqualTo(6); // the blank line is skipped
        assertThat(report.get("validRows").asInt()).isEqualTo(2);
        assertThat(report.get("invalidRows").asInt()).isEqualTo(4);
        assertThat(errors(report)).contains(
                "3 email: duplicate of row 2",
                "3 country_code: unknown country",
                "3 job_title: not a job title in Sales",
                "3 job_level: must be L1, L2, L3, L4 or L5",
                "3 hire_date: can't be in the future",
                "3 annual_salary: must be a whole number with no symbols, like 78000",
                "4 salary_effective_date: must be between the hire date and today",
                "5 email: already used by an existing employee",
                "5 currency: must be EUR for country FR",
                "6 salary_reason: only allowed when salary_effective_date is after hire_date");
        // Row numbers are file lines (header = 1), so the row after the blank line is 8, as in Excel.
        assertThat(report.get("sample").get(0).get("row").asInt()).isEqualTo(2);
        assertThat(report.get("sample").get(1).get("row").asInt()).isEqualTo(8);
        assertThat(report.get("errorsTruncated").asBoolean()).isFalse();

        assertThat(count("employee")).isEqualTo(employeesBefore);
    }

    @Test
    void unreadableFilesAreRejected() throws Exception {
        preview(csv("cols.csv", "first_name,last_name\nAna,Silva"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"))
                .andExpect(jsonPath("$.detail", containsString("Missing columns: email")));
        preview(csv("empty.csv", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        preview(csv("header-only.csv", HEADER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file has a header row but no employees"));
        preview(new MockMultipartFile("file", "latin1.csv", "text/csv",
                (HEADER + "\nRené,Dubois").getBytes(StandardCharsets.ISO_8859_1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("UTF-8")));
        mvc.perform(multipart("/api/employees/import/preview").session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    void moreThan10000RowsIsTooLarge() throws Exception {
        StringBuilder file = new StringBuilder(HEADER);
        for (int i = 0; i <= ImportFormat.MAX_ROWS; i++) {
            file.append("\nA,B,a").append(i).append("@example.com,DE,Engineering,Software Engineer,L1,2022-01-01,1,EUR,,");
        }
        preview(csv("big.csv", file.toString()))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void importSavesEveryRowWithItsFirstSalaryAndAnAuditEntry() throws Exception {
        long lastCode = Long.parseLong(jdbc.queryForObject("SELECT MAX(employee_code) FROM employee", String.class)
                .substring(4));
        // A byte-order mark and Windows line endings, as Excel writes them.
        String file = "﻿" + String.join("\r\n", HEADER,
                "Ines,Moreau,Ines.Moreau@Example.com,FR,Design,Product Designer,L2,2023-02-01,52000,EUR,,",
                "Arjun,Rao,arjun.rao@example.com,IN,Engineering,Data Engineer,l3,2021-07-01,2400000,inr,2025-04-01,promotion",
                "Mia,Clark,mia.clark@example.com,US,Sales,Account Executive,L2,2022-03-01,80000,USD,2024-03-01,") + "\r\n";

        mvc.perform(multipart("/api/employees/import").file(csv("people.csv", file)).session(session).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("people.csv"))
                .andExpect(jsonPath("$.importedCount").value(3));

        Map<String, Object> ines = jdbc.queryForMap("SELECT * FROM employee WHERE email = 'ines.moreau@example.com'");
        assertThat(ines.get("employee_code")).isEqualTo(String.format("EMP-%06d", lastCode + 1));
        assertThat(ines.get("current_salary_effective_date")).isEqualTo("2023-02-01");

        // Reason stored on the first salary row: NEW_HIRE, the given reason, or "not recorded" (null).
        assertThat(reasonOf("ines.moreau@example.com")).isEqualTo("NEW_HIRE");
        assertThat(reasonOf("arjun.rao@example.com")).isEqualTo("PROMOTION");
        assertThat(reasonOf("mia.clark@example.com")).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM salary_history h JOIN employee e ON e.id = h.employee_id
                WHERE e.email IN ('ines.moreau@example.com', 'arjun.rao@example.com', 'mia.clark@example.com')
                  AND h.previous_amount IS NULL""", Integer.class)).isEqualTo(3);

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT a.action, a.performed_by, a.changes FROM audit_log a JOIN employee e ON e.id = a.employee_id
                WHERE e.email = 'arjun.rao@example.com'""");
        assertThat(audit.get("action")).isEqualTo("IMPORT");
        assertThat(audit.get("performed_by")).isEqualTo("admin");
        assertThat((String) audit.get("changes")).contains("\"reason\":{\"to\":\"PROMOTION\"}");
    }

    @Test
    void anImportWithErrorsSavesNothing() throws Exception {
        int employeesBefore = count("employee");
        String file = String.join("\n", HEADER,
                "Ok,Person,ok.person@example.com,DE,Engineering,Software Engineer,L2,2022-01-10,60000,EUR,,",
                "Bad,Person,bad.person@example.com,DE,Engineering,Software Engineer,L2,2022-01-10,60000,USD,,");
        mvc.perform(multipart("/api/employees/import").file(csv("mixed.csv", file)).session(session).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"))
                .andExpect(jsonPath("$.report.invalidRows").value(1))
                .andExpect(jsonPath("$.report.errors[0].row").value(3));
        assertThat(count("employee")).isEqualTo(employeesBefore);
    }

    @Test
    void theSeedersSampleFileImportsIntoASeededDatabase() throws Exception {
        byte[] sample = Files.readAllBytes(tempDir.resolve("sample.csv"));
        long rows = new String(sample, StandardCharsets.UTF_8).lines().count() - 1;
        mvc.perform(multipart("/api/employees/import").session(session).with(csrf())
                        .file(new MockMultipartFile("file", "sample.csv", "text/csv", sample)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(rows));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private ResultActions preview(MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/employees/import/preview").file(file).session(session).with(csrf()));
    }

    private static MockMultipartFile csv(String name, String text) {
        return new MockMultipartFile("file", name, "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static List<String> errors(JsonNode report) {
        return report.get("errors").valueStream()
                .map(e -> e.get("row").asInt() + " " + e.get("column").asString() + ": " + e.get("message").asString())
                .toList();
    }

    private String reasonOf(String email) {
        return jdbc.queryForObject("""
                SELECT h.reason FROM salary_history h JOIN employee e ON e.id = h.employee_id WHERE e.email = ?""",
                String.class, email);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
