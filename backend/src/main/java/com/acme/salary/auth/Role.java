package com.acme.salary.auth;

/**
 * What a signed-in user may do. A super admin can do everything an HR manager can, and also manages
 * the HR users (US-22). Spring Security sees these as authorities {@code ROLE_SUPER_ADMIN} and
 * {@code ROLE_HR_MANAGER}.
 */
public enum Role {
    SUPER_ADMIN,
    HR_MANAGER
}
