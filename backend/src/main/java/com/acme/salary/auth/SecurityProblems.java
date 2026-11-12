package com.acme.salary.auth;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401 and 403 responses from the security filters, in the same problem+json shape as every
 * other API error (api-contracts.md section 2). These happen before any controller runs, so the
 * global exception handler can't produce them.
 */
@Component
class SecurityProblems {

    private final JsonMapper jsonMapper;

    SecurityProblems(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** No session, an expired one, or a user who has since been disabled. */
    void unauthenticated(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "Unauthenticated", "Please sign in.", "UNAUTHENTICATED");
    }

    /** Signed in but not allowed (an HR manager calling a super admin endpoint), or a missing CSRF token. */
    void forbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "Forbidden", "You don't have access to this.", "FORBIDDEN");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String title,
                       String detail, String code) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
