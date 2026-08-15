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
// development-profile fallback — "development" meaning that one profile and no other, including the
// "demo" profile a public deployment runs the seed under. Boots the real application (headless)
// against a real Mongo, because Mongock needs one regardless of which property fails first.
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

    // The deployed demo's own acceptance bullet, from Issue #12: "No development default reaches it, and
    // the application would refuse to start if one were missing." The deployment wants the demo seed
    // without the developer conveniences, which is the whole reason "demo" is a separate profile from
    // "development" — and the difference between them is only real if it is the difference this test
    // asserts.
    @Test
    void refusesToStartWithoutTheCheckInHmacSecretInTheDemoProfile() {
        SpringApplicationBuilder builder = applicationBuilder("secrets-test-demo-no-hmac")
                .profiles("demo")
                .properties("SESSION_SECRET=some-session-secret");

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
