package com.acme.salary.auth;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The signed-in user's username, taken from the session (US-20): {@code changed_by} and audit entries are
 * never taken from the request body. Work that runs outside a request, such as start-up tasks, is
 * attributed to {@value #SYSTEM}.
 */
@Component
class SessionCurrentUser implements CurrentUser {

    static final String SYSTEM = "system";

    @Override
    public String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        return authentication.getName();
    }
}
