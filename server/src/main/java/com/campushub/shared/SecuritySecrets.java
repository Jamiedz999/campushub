package com.campushub.shared;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// No production default anywhere, including the development profile: the session secret is
// always supplied by the environment. Binding fails startup outside a well-formed environment.
@ConfigurationProperties(prefix = "campushub.security")
@Validated
public class SecuritySecrets {

    @NotBlank
    private String sessionSecret;

    public String getSessionSecret() {
        return sessionSecret;
    }

    public void setSessionSecret(String sessionSecret) {
        this.sessionSecret = sessionSecret;
    }
}
