package com.uday.jobfinder.web;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Login for a single owner. Three ways in:
 * <ul>
 *   <li>Username/password from APP_USERNAME / APP_PASSWORD (random password logged if unset).</li>
 *   <li>"Sign in with GitHub", enabled when GITHUB_CLIENT_ID/SECRET are set. Only GitHub usernames
 *       listed in ALLOWED_GITHUB_USERS get in.</li>
 *   <li>"Sign in with Google", enabled when GOOGLE_CLIENT_ID/SECRET are set. Only verified emails
 *       listed in ALLOWED_GOOGLE_EMAILS get in.</li>
 * </ul>
 * An empty allowlist lets nobody in through that provider: the whole app holds your resume.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    static final String OWNER_ATTRIBUTE = "jobfinder_owner";

    private final String ownerUsername;

    public SecurityConfig(@Value("${APP_USERNAME:uday}") String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(@Value("${APP_PASSWORD:}") String password, PasswordEncoder encoder) {
        if (password.isBlank()) {
            byte[] bytes = new byte[12];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            log.warn("APP_PASSWORD is not set. Generated password for user '{}': {}", ownerUsername, password);
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(ownerUsername).password(encoder.encode(password)).roles("OWNER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, UserDetailsService users,
                                 @Value("${APP_PASSWORD:}") String rememberMeKey,
                                 @Value("${GITHUB_CLIENT_ID:}") String githubId,
                                 @Value("${GITHUB_CLIENT_SECRET:}") String githubSecret,
                                 @Value("${GOOGLE_CLIENT_ID:}") String googleId,
                                 @Value("${GOOGLE_CLIENT_SECRET:}") String googleSecret,
                                 @Value("${ALLOWED_GITHUB_USERS:}") String allowedGithub,
                                 @Value("${ALLOWED_GOOGLE_EMAILS:}") String allowedGoogle) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/login.html", "/app.css", "/auth/info", "/error", "/favicon.ico").permitAll()
                    .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
            .rememberMe(rm -> rm
                    .userDetailsService(users)
                    .key(rememberMeKey.isBlank() ? "jobfinder-local" : rememberMeKey)
                    .tokenValiditySeconds(30 * 24 * 3600)
                    .alwaysRemember(true))
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            // JSON API answers 401 (the page then redirects to /login) instead of an HTML redirect.
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
            // The API is only called from our own page, and the session cookie is SameSite=Lax
            // (application.properties), so cross-site POST/PATCH/DELETE requests never carry it.
            // /logout is exempt too because the static page has no CSRF token (worst case: a forced logout).
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/logout"));

        List<ClientRegistration> providers = new ArrayList<>();
        if (!githubId.isBlank() && !githubSecret.isBlank()) {
            providers.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(githubId).clientSecret(githubSecret).clientName("GitHub").build());
        }
        if (!googleId.isBlank() && !googleSecret.isBlank()) {
            // Plain OAuth2 scopes (no "openid") so Google goes through the same user service as GitHub.
            providers.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleId).clientSecret(googleSecret).clientName("Google")
                    .scope("email", "profile").build());
        }
        if (!providers.isEmpty()) {
            Set<String> githubUsers = parseList(allowedGithub);
            Set<String> googleEmails = parseList(allowedGoogle);
            log.info("Social login enabled for {} (allowed GitHub users: {}, allowed Google emails: {})",
                    providers.stream().map(ClientRegistration::getRegistrationId).toList(),
                    githubUsers.size(), googleEmails.size());
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(new InMemoryClientRegistrationRepository(providers))
                    .loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .userInfoEndpoint(info -> info.userService(
                            allowlistedUserService(new DefaultOAuth2UserService(), githubUsers, googleEmails))));
        }
        return http.build();
    }

    /**
     * Wraps the normal user lookup: rejects anyone not on the allowlist, and signs allowed people in
     * as the owner account so "remember me" and everything else treats them the same.
     */
    OAuth2UserService<OAuth2UserRequest, OAuth2User> allowlistedUserService(
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
            Set<String> githubUsers, Set<String> googleEmails) {
        return request -> {
            OAuth2User user = delegate.loadUser(request);
            String provider = request.getClientRegistration().getRegistrationId();
            if (!isAllowed(provider, user.getAttributes(), githubUsers, googleEmails)) {
                log.warn("Rejected {} login for {}", provider, describe(provider, user.getAttributes()));
                throw new OAuth2AuthenticationException(new OAuth2Error("access_denied",
                        "This " + provider + " account is not allowed to sign in.", null));
            }
            Map<String, Object> attributes = new HashMap<>(user.getAttributes());
            attributes.put(OWNER_ATTRIBUTE, ownerUsername);
            return new OwnerOAuth2User(attributes);
        };
    }

    /**
     * A social login that acts as the owner account. Implementing UserDetails lets the
     * remember-me cookie work exactly as it does for password logins.
     */
    static final class OwnerOAuth2User extends DefaultOAuth2User implements UserDetails {
        OwnerOAuth2User(Map<String, Object> attributes) {
            super(AuthorityUtils.createAuthorityList("ROLE_OWNER"), attributes, OWNER_ATTRIBUTE);
        }

        @Override
        public String getUsername() {
            return getName();
        }

        @Override
        public String getPassword() {
            return null; // remember-me then looks up the owner's password hash itself
        }
    }

    static boolean isAllowed(String provider, Map<String, Object> attrs,
                             Set<String> githubUsers, Set<String> googleEmails) {
        return switch (provider) {
            case "github" -> attrs.get("login") instanceof String login
                    && githubUsers.contains(login.toLowerCase(Locale.ROOT));
            case "google" -> Boolean.TRUE.equals(attrs.get("email_verified"))
                    && attrs.get("email") instanceof String email
                    && googleEmails.contains(email.toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    static Set<String> parseList(String csv) {
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static Object describe(String provider, Map<String, Object> attrs) {
        return "github".equals(provider) ? attrs.get("login") : attrs.get("email");
    }
}
