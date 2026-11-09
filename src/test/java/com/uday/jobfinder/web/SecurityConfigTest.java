package com.uday.jobfinder.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class SecurityConfigTest {

    private static final Set<String> GITHUB = SecurityConfig.parseList(" UdayKuk , other ");
    private static final Set<String> GOOGLE = SecurityConfig.parseList("Me@Gmail.com");

    @Test
    void githubAllowlistIsCaseInsensitive() {
        assertTrue(SecurityConfig.isAllowed("github", Map.of("login", "udaykuk"), GITHUB, GOOGLE));
        assertFalse(SecurityConfig.isAllowed("github", Map.of("login", "stranger"), GITHUB, GOOGLE));
        assertFalse(SecurityConfig.isAllowed("github", Map.of(), GITHUB, GOOGLE));
    }

    @Test
    void googleNeedsAllowedAndVerifiedEmail() {
        assertTrue(SecurityConfig.isAllowed("google",
                Map.of("email", "me@gmail.com", "email_verified", true), GITHUB, GOOGLE));
        assertFalse(SecurityConfig.isAllowed("google",
                Map.of("email", "me@gmail.com", "email_verified", false), GITHUB, GOOGLE));
        assertFalse(SecurityConfig.isAllowed("google",
                Map.of("email", "someone@gmail.com", "email_verified", true), GITHUB, GOOGLE));
    }

    @Test
    void emptyAllowlistLetsNobodyIn() {
        assertFalse(SecurityConfig.isAllowed("github", Map.of("login", "udaykuk"), Set.of(), Set.of()));
        assertFalse(SecurityConfig.isAllowed("unknown", Map.of("login", "udaykuk"), GITHUB, GOOGLE));
    }

    @Test
    void allowedUserBecomesOwnerAndStrangerIsRejected() {
        var config = new SecurityConfig("uday");
        var request = new OAuth2UserRequest(
                CommonOAuth2Provider.GITHUB.getBuilder("github").clientId("id").clientSecret("s").build(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "t", Instant.now(), Instant.now().plusSeconds(60)));

        var allowed = config.allowlistedUserService(r -> githubUser("UdayKuk"), GITHUB, GOOGLE);
        OAuth2User owner = allowed.loadUser(request);
        assertEquals("uday", owner.getName());
        assertEquals("uday", assertInstanceOf(UserDetails.class, owner).getUsername());

        var rejected = config.allowlistedUserService(r -> githubUser("stranger"), GITHUB, GOOGLE);
        assertThrows(OAuth2AuthenticationException.class, () -> rejected.loadUser(request));
    }

    private static OAuth2User githubUser(String login) {
        return new DefaultOAuth2User(Set.of(), Map.of("login", login, "id", 1), "login");
    }
}
