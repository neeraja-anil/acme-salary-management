package com.acme.salary.auth;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/users}: super admins only (enforced in {@link SecurityConfig}). */
@RestController
@RequestMapping("/api/users")
class UserController {

    /** BCrypt only reads the first 72 bytes of a password, so longer ones are refused rather than cut short. */
    record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
                    message = "must be 3-50 letters, digits, dots, dashes or underscores") String username,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters") String password) {
    }

    record UpdateUserRequest(@NotNull Boolean enabled) {
    }

    record ResetPasswordRequest(
            @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters") String password) {
    }

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    List<UserResponse> list() {
        return userService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createHrManager(request.username(), request.displayName(), request.password());
    }

    @PatchMapping("/{id}")
    UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.setEnabled(id, request.enabled());
    }

    @PutMapping("/{id}/password")
    ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }
}
