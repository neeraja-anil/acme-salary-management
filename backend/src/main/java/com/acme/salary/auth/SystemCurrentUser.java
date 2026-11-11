package com.acme.salary.auth;

import org.springframework.stereotype.Component;

/**
 * Placeholder until sign-in (US-18) is implemented: every change is attributed to "system".
 * Will be replaced by an implementation that reads the authenticated session.
 */
@Component
public class SystemCurrentUser implements CurrentUser {

    @Override
    public String username() {
        return "system";
    }
}
