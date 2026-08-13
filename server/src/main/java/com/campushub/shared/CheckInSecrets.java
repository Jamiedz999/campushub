package com.campushub.shared;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// The only secret with a development default (application-development.yml), so a fresh clone
// runs without it. Every other profile must supply CHECKIN_HMAC_SECRET or fail to start.
@ConfigurationProperties(prefix = "campushub.checkin")
@Validated
public class CheckInSecrets {

    @NotBlank
    private String hmacSecret;

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }
}
