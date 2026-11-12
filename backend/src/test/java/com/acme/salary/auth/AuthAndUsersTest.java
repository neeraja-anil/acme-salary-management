package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sign-in, roles and user management through the real security filter chain (US-18 to US-20, US-22),
 * on a fresh SQLite file with a known super admin password.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthAndUsersTest {

    private static final String ADMIN_PASSWORD = "admin-test-password";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + tempDir.resolve("auth-test.db") + "?foreign_keys=true&journal_mode=WAL");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.admin.password", () -> ADMIN_PASSWORD);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void apiNeedsASignedInUser() throws Exception {
        mvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCredentialsGetTheSameGenericAnswer() throws Exception {
        for (Map<String, String> body : new Map[] {
                Map.of("username", "admin", "password", "wrong-password"),
                Map.of("username", "nobody", "password", ADMIN_PASSWORD)}) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid credentials"));
        }
    }

    @Test
    void changesWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "admin", "password", ADMIN_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * Runs in a fresh context: spring-security-test's {@code csrf()} helper, used by the other tests, permanently
     * wraps the app's CSRF token store so it only writes cookies for requests made through the helper.
     */
    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void csrfEndpointSetsTheCookieAngularReads() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        // Angular reads this cookie and sends it back as the X-XSRF-TOKEN header, so it must not be HttpOnly.
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=").doesNotContainIgnoringCase("HttpOnly"));
    }

    @Test
    void superAdminSignsInAndSeesUsers() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        mvc.perform(get("/api/auth/me").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/users").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
        // A super admin can use the salary app too.
        mvc.perform(get("/api/employees").session(admin)).andExpect(status().isOk());
    }

    @Test
    void superAdminAddsAnHrManagerWhoCanUseTheAppButNotManageUsers() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        createHrManager(admin, "Lena.HR", "Lena Fischer", "lena-password-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("Lena.HR")) // kept exactly as entered
                .andExpect(jsonPath("$.role").value("HR_MANAGER"));

        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username = 'Lena.HR'", String.class))
                .startsWith("$2"); // BCrypt, never the plain password

        MockHttpSession hr = signIn("Lena.HR", "lena-password-1");
        mvc.perform(get("/api/employees").session(hr)).andExpect(status().isOk());
        mvc.perform(get("/api/users").session(hr))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void usernamesAreCaseSensitiveButCantDifferOnlyInCase() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        createHrManager(admin, "Noor.Iyer", "Noor Iyer", "noor-password-1").andExpect(status().isCreated());

        // Sign-in needs the exact case.
        for (String wrongCase : new String[] {"noor.iyer", "NOOR.IYER"}) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", wrongCase, "password", "noor-password-1"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid credentials"));
        }
        signIn("Noor.Iyer", "noor-password-1");

        // A look-alike that differs only in case is refused, including "Admin" next to "admin".
        createHrManager(admin, "noor.iyer", "Someone Else", "another-password")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
        createHrManager(admin, "Admin", "Fake Admin", "another-password")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    void disablingAUserEndsTheirOpenSessionAndBlocksSignIn() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        long id = idOf(createHrManager(admin, "omar.hr", "Omar Haddad", "omar-password-1")
                .andExpect(status().isCreated()).andReturn());
        MockHttpSession hr = signIn("omar.hr", "omar-password-1");

        mvc.perform(patch("/api/users/" + id).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/employees").session(hr)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "omar.hr", "password", "omar-password-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void passwordResetReplacesTheOldPassword() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        long id = idOf(createHrManager(admin, "maya.hr", "Maya Wong", "maya-password-1")
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(put("/api/users/" + id + "/password").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"maya-password-2\"}"))
                .andExpect(status().isNoContent());
        signIn("maya.hr", "maya-password-2");
    }

    @Test
    void superAdminAccountsCantBeChangedThroughTheUsersApi() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        Long adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'admin'", Long.class);
        mvc.perform(patch("/api/users/" + adminId).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("SUPER_ADMIN_PROTECTED"));
    }

    @Test
    void weakPasswordsAndBadUsernamesAreRejected() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        createHrManager(admin, "x", "X", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        mvc.perform(post("/api/auth/logout").session(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(status().isUnauthorized());
    }

    @Test
    void changesAreAttributedToTheSignedInUser() throws Exception {
        MockHttpSession admin = signIn("admin", ADMIN_PASSWORD);
        createHrManager(admin, "priya.hr", "Priya Sharma", "priya-password-1").andExpect(status().isCreated());
        MockHttpSession hr = signIn("priya.hr", "priya-password-1");

        Long jobTitleId = jdbc.queryForObject("SELECT id FROM job_title WHERE name = 'Software Engineer'", Long.class);
        Map<String, Object> employee = Map.of("firstName", "Test", "lastName", "Person",
                "email", "test.person@acme.example", "countryCode", "DE", "departmentId", 1,
                "jobTitleId", jobTitleId, "jobLevel", "L2", "hireDate", "2024-01-15", "startingSalary", 60000);
        mvc.perform(post("/api/employees").session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(employee)))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "SELECT performed_by FROM audit_log ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("priya.hr");
        assertThat(jdbc.queryForObject(
                "SELECT changed_by FROM salary_history ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("priya.hr");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private MockHttpSession signIn(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions createHrManager(MockHttpSession session, String username,
                                                                             String displayName, String password)
            throws Exception {
        return mvc.perform(post("/api/users").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        Map.of("username", username, "displayName", displayName, "password", password))));
    }

    private long idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
