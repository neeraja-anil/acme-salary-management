package com.acme.salary.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All "today"/"now" decisions go through an injected {@link Clock}, never {@code LocalDate.now()},
 * so date rules (e.g. "hire date not in the future") are deterministic in tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
