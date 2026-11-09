package com.uday.jobfinder.web;

import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Single-user login. The whole app (resume, tracker, Gemini quota) is private to you.
 * Credentials come from APP_USERNAME / APP_PASSWORD (in .env locally, env vars on the server).
 * If no password is set, a random one is generated and printed in the logs at startup.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(@Value("${APP_USERNAME:uday}") String username,
                             @Value("${APP_PASSWORD:}") String password,
                             PasswordEncoder encoder) {
        if (password.isBlank()) {
            byte[] bytes = new byte[12];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            log.warn("APP_PASSWORD is not set. Generated password for user '{}': {}", username, password);
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(encoder.encode(password)).roles("OWNER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, UserDetailsService users,
                                 @Value("${APP_PASSWORD:}") String rememberMeKey) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/error", "/favicon.ico").permitAll()
                    .anyRequest().authenticated())
            .formLogin(form -> form.defaultSuccessUrl("/", true))
            .rememberMe(rm -> rm
                    .userDetailsService(users)
                    .key(rememberMeKey.isBlank() ? "jobfinder-local" : rememberMeKey)
                    .tokenValiditySeconds(30 * 24 * 3600)
                    .alwaysRemember(true))
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            // JSON API answers 401 (the page then redirects to /login) instead of an HTML redirect.
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
            // The API is only called from our own page; the session cookie is SameSite=Strict
            // (application.properties), so cross-site requests never carry it.
            // /logout is exempt too because the static page has no CSRF token (worst case: a forced logout).
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/logout"));
        return http.build();
    }
}
