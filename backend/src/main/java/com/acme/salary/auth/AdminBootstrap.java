package com.acme.salary.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first super admin when none exists, so a fresh installation can be signed in to. No
 * credentials live in the repository: the password comes from {@code APP_ADMIN_PASSWORD}, or is generated
 * and written to the log once. An existing super admin is never changed, so restarts are harmless.
 */
@Component
@Order(0) // before the demo data seed
@EnableConfigurationProperties(AdminProperties.class)
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String PASSWORD_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;
    private final Clock clock;

    AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder, AdminProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.SUPER_ADMIN)) {
            return;
        }
        String username = properties.username().trim();
        boolean generated = properties.password() == null || properties.password().isBlank();
        String password = generated ? randomPassword() : properties.password();
        users.save(new AppUser(username, passwordEncoder.encode(password), properties.displayName(),
                Role.SUPER_ADMIN, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)));
        if (generated) {
            log.warn("Created super admin '{}' with generated password: {}  (set APP_ADMIN_PASSWORD to choose one)",
                    username, password);
        } else {
            log.info("Created super admin '{}' from APP_ADMIN_PASSWORD", username);
        }
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            password.append(PASSWORD_CHARACTERS.charAt(random.nextInt(PASSWORD_CHARACTERS.length())));
        }
        return password.toString();
    }
}
