package com.acme.salary.auth;

/**
 * The user performing the current request, used for {@code changed_by} / {@code performed_by}.
 * Services depend on this abstraction so that adding authentication later only swaps the implementation.
 */
public interface CurrentUser {

    String username();
}
