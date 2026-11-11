package com.acme.salary.common.error;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 7807 problem response with a stable {@code code}
 * (see api-contracts.md section 2). Extends {@link ResponseEntityExceptionHandler} so that
 * standard Spring MVC errors (405, 415, ...) get the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Application errors ------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(ex.status(), ex.title(), ex.getMessage(), ex.code(), request);
        ex.properties().forEach(problem::setProperty);
        return problem;
    }

    /** Two concurrent writes to the same employee: JPA's @Version check caught it at flush time. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        ConflictException conflict = ConflictException.staleVersion();
        return problem(conflict.status(), conflict.title(), conflict.getMessage(), conflict.code(), request);
    }

    /** A database constraint caught something the service checks missed (e.g. a race on a unique column). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The change conflicts with existing data. Reload and try again.", "DATA_CONFLICT", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "Something went wrong. Please try again.", "INTERNAL_ERROR", request);
    }

    // ---- Spring MVC errors -------------------------------------------------------------------

    /** Bean validation failed on a request body (@Valid). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldError::field))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "Request has " + errors.size() + " invalid field" + (errors.size() == 1 ? "" : "s"),
                "VALIDATION_FAILED", request);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Constraint annotations on @RequestParam / @PathVariable failed (e.g. size > 100). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(parameterName(result), error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Request has invalid parameters", "INVALID_PARAMETER", request);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** A parameter could not be converted, e.g. {@code /employees/abc} or {@code status=FOO}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'",
                "INVALID_PARAMETER", request);
    }

    /** Malformed JSON, or a value of the wrong type / unknown enum constant in the body. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "Request body is malformed or contains a value of the wrong type", "VALIDATION_FAILED", request);
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Every other Spring MVC error (405, 415, 404 for unknown paths, ...): keep Spring's status and text,
     * add our {@code code}. Spring builds the body inside the superclass, so we decorate its result.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty("code", status != null ? status.name() : "ERROR");
            problem.setInstance(URI.create(path(request)));
        }
        return response;
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code,
                                         HttpServletRequest request) {
        return problem(status, title, detail, code, request.getRequestURI());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code,
                                         WebRequest request) {
        return problem(status, title, detail, code, path(request));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        return problem;
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "";
    }

    private static String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "parameter";
    }
}
