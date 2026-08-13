package com.campushub.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campushub.CampusHubApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// Proves the fail-loudly contract from docs/adr/15-define-http-api-and-time-contract.md: every secret
// is a named property with no production default, and only the check-in HMAC secret gets a
// development-profile fallback. Boots the real application (headless) against a real Mongo, because
// Mongock needs one regardless of which property fails first.
@Testcontainers
class SecretsStartupIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @Test
    void refusesToStartWithoutTheSessionSecret() {
        SpringApplicationBuilder builder =
                applicationBuilder("secrets-test-no-session").properties("CHECKIN_HMAC_SECRET=some-hmac-secret");

        assertThatThrownBy(builder::run).isInstanceOf(RuntimeException.class);
    }

    @Test
    void refusesToStartWithoutTheCheckInHmacSecretOutsideTheDevelopmentProfile() {
        SpringApplicationBuilder builder =
                applicationBuilder("secrets-test-no-hmac").properties("SESSION_SECRET=some-session-secret");

        assertThatThrownBy(builder::run).isInstanceOf(RuntimeException.class);
    }

    @Test
    void startsWithTheDevelopmentDefaultForTheCheckInHmacSecretInTheDevelopmentProfile() {
        SpringApplicationBuilder builder = applicationBuilder("secrets-test-dev-default")
                .profiles("development")
                .properties("SESSION_SECRET=some-session-secret");

        ConfigurableApplicationContext context = builder.run();
        try {
            assertThat(context.getBean(CheckInSecrets.class).getHmacSecret()).isNotBlank();
        } finally {
            context.close();
        }
    }

    private static SpringApplicationBuilder applicationBuilder(String databaseName) {
        return new SpringApplicationBuilder(CampusHubApplication.class)
                .web(WebApplicationType.NONE)
                .properties("MONGODB_URI=" + MONGO_DB.getConnectionString() + "/" + databaseName);
    }
}
