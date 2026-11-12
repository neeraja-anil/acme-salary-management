package com.acme.salary.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The first super admin, created at start-up when there is none (US-18). Set the password with
 * {@code APP_ADMIN_PASSWORD}; if it is blank, a random one is generated and logged once.
 */
@ConfigurationProperties("app.admin")
record AdminProperties(
        @DefaultValue("admin") String username,
        @DefaultValue("Super admin") String displayName,
        String password) {
}
