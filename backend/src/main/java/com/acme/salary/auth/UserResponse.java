package com.acme.salary.auth;

import java.time.Instant;

/** A user as the API shows them: never the password hash. Returned by login, {@code /me} and the users list. */
public record UserResponse(Long id, String username, String displayName, Role role, boolean enabled,
                           Instant createdAt) {

    static UserResponse of(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                user.isEnabled(), user.getCreatedAt());
    }
}
