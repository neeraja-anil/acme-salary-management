package com.acme.salary.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends the session of a user who has been disabled (or removed) since they signed in, so a super admin
 * disabling someone takes effect on that person's next request rather than when their session times out.
 * One indexed lookup per request, which is negligible here. Not a Spring bean on purpose: as a bean,
 * Spring Boot would also register it as a plain servlet filter and run it twice.
 */
class ActiveUserFilter extends OncePerRequestFilter {

    private final AppUserRepository users;

    ActiveUserFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            boolean stillActive = users.findByUsername(authentication.getName())
                    .map(AppUser::isEnabled)
                    .orElse(false);
            if (!stillActive) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext(); // the request continues as anonymous, so /api gets 401
            }
        }
        chain.doFilter(request, response);
    }
}
