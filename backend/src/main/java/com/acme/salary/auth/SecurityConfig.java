package com.acme.salary.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Sign-in with a server-side session (US-18, US-19), as chosen in architecture.md section 10:
 * <ul>
 *   <li>The session cookie is HttpOnly, so scripts can't read it; it expires after 30 idle minutes
 *       (server.servlet.session in application.yml).</li>
 *   <li>CSRF: the readable {@code XSRF-TOKEN} cookie, echoed back by Angular in the {@code X-XSRF-TOKEN}
 *       header on every POST/PUT/PATCH/DELETE.</li>
 *   <li>Everything under {@code /api} needs a signed-in user except getting a CSRF token and signing in.
 *       {@code /api/users} is for super admins only.</li>
 *   <li>Other paths (the Angular app's own files) are open: the app asks the API, which checks.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblems problems, AppUserRepository users,
                                            CookieCsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/users/**").hasRole(Role.SUPER_ADMIN.name())
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> problems.unauthenticated(request, response))
                        .accessDeniedHandler((request, response, ex) -> problems.forbidden(request, response)))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID"))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable()) // an API never redirects to a "saved" page after sign-in
                .addFilterBefore(new ActiveUserFilter(users), AuthorizationFilter.class);
        return http.build();
    }

    /** The readable CSRF cookie Angular expects. A bean, so sign-in can issue a fresh token for the new session. */
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
