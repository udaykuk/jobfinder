package com.uday.jobfinder.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Serves the custom login page and tells it which sign-in options are switched on. */
@Controller
public class AuthInfoController {

    private final List<String> providers = new ArrayList<>();

    public AuthInfoController(@Value("${GITHUB_CLIENT_ID:}") String githubId,
                              @Value("${GITHUB_CLIENT_SECRET:}") String githubSecret,
                              @Value("${GOOGLE_CLIENT_ID:}") String googleId,
                              @Value("${GOOGLE_CLIENT_SECRET:}") String googleSecret) {
        if (!googleId.isBlank() && !googleSecret.isBlank()) providers.add("google");
        if (!githubId.isBlank() && !githubSecret.isBlank()) providers.add("github");
    }

    @GetMapping("/login")
    public String loginPage() {
        return "forward:/login.html";
    }

    /** Public: the CSRF token for the password form plus the enabled social providers. */
    @GetMapping("/auth/info")
    @ResponseBody
    public Map<String, Object> info(CsrfToken csrf) {
        return Map.of("csrfParam", csrf.getParameterName(), "csrfToken", csrf.getToken(), "providers", providers);
    }
}
