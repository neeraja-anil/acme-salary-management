package com.acme.salary.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.acme.salary.common.error.BusinessRuleException;
import com.acme.salary.common.error.ConflictException;
import com.acme.salary.common.error.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management for super admins (US-22): add HR managers, disable or enable them, and reset their
 * passwords. Super admin accounts can't be changed here, so nobody can lock the last super admin out.
 */
@Service
public class UserService {

    static final String DUPLICATE_USERNAME = "DUPLICATE_USERNAME";
    static final String RULE_SUPER_ADMIN_PROTECTED = "SUPER_ADMIN_PROTECTED";

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final Clock clock;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, CurrentUser currentUser,
                       Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByRoleDescUsernameAsc().stream().map(UserResponse::of).toList();
    }

    /** New users are always HR managers; the first super admin comes from start-up settings. */
    @Transactional
    public UserResponse createHrManager(String username, String displayName, String password) {
        // Case-sensitive: stored exactly as entered, and sign-in must match it. A name that differs from an
        // existing one only in case is refused, so "Admin" can't pose as "admin" in the change log.
        String trimmed = username.trim();
        if (users.existsByUsernameIgnoreCase(trimmed)) {
            throw new ConflictException(DUPLICATE_USERNAME, "Username " + trimmed + " is already taken");
        }
        AppUser user = users.save(new AppUser(trimmed, passwordEncoder.encode(password), displayName.trim(),
                Role.HR_MANAGER, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)));
        log.info("{} added HR manager '{}'", currentUser.username(), trimmed);
        return UserResponse.of(user);
    }

    /** A disabled user can't sign in, and an open session of theirs ends on its next request. */
    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled) {
        AppUser user = loadHrManager(id);
        user.setEnabled(enabled);
        log.info("{} {} user '{}'", currentUser.username(), enabled ? "enabled" : "disabled", user.getUsername());
        return UserResponse.of(user);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        AppUser user = loadHrManager(id);
        user.changePassword(passwordEncoder.encode(password));
        log.info("{} reset the password of user '{}'", currentUser.username(), user.getUsername());
    }

    private AppUser loadHrManager(Long id) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        if (user.isSuperAdmin()) {
            throw new BusinessRuleException(RULE_SUPER_ADMIN_PROTECTED, "Super admin accounts can't be changed here");
        }
        return user;
    }
}
