package com.acme.salary.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    /** Usernames are case-sensitive, but "Lena" and "lena" can't both exist: they would look like one person. */
    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRole(Role role);

    List<AppUser> findAllByOrderByRoleDescUsernameAsc();
}
