package com.acme.salary.auth;

import com.acme.salary.common.error.UnauthenticatedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in endpoints (api-contracts.md section 3). Sign-out is {@code POST /api/auth/logout}, handled by
 * Spring Security's logout filter (see {@link SecurityConfig}).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final AppUserRepository users;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
                   CookieCsrfTokenRepository csrfTokenRepository, AppUserRepository users) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.users = users;
    }

    /** Called when the app starts: sets the {@code XSRF-TOKEN} cookie (reading the token is what writes it). */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                       HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (AuthenticationException ex) {
            // Same answer for an unknown user, a wrong password and a disabled account.
            throw new UnauthenticatedException("Invalid credentials");
        }

        // A new session id at sign-in, so an id planted in the browser beforehand is useless (session fixation).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // And a fresh CSRF token for the signed-in session.
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);

        return UserResponse.of(users.findByUsername(authentication.getName()).orElseThrow());
    }

    /** The signed-in user; 401 (from the security filters) when there is none. */
    @GetMapping("/me")
    UserResponse me(Authentication authentication) {
        return UserResponse.of(users.findByUsername(authentication.getName()).orElseThrow());
    }
}
