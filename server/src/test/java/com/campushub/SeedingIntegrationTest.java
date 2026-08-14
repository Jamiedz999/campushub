package com.campushub;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.club.domain.Club;
import com.campushub.identityaccess.domain.Account;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// Proves the two-change-unit split from Issue #2: the structural change unit (accounts.email index,
// the University Admin account) always runs; the demo-data change unit (Clubs, Club Officers, extra
// Students) runs only under the "development" profile. Boots the real application via Mongock, not the
// change units directly, because the thing actually being proven is Mongock's own @Profile handling.
@Testcontainers
class SeedingIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @Test
    void aFreshNonDevelopmentDatabaseHasAWorkingAdminSignInAndNoDemoData() {
        ConfigurableApplicationContext context = boot("seeding-test-no-profile", null);
        try {
            MongoTemplate mongoTemplate = context.getBean(MongoTemplate.class);
            PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);

            Account admin = mongoTemplate.findAll(Account.class).stream()
                    .filter(account -> account.getEmail().equals("admin@demo.campushub"))
                    .findFirst()
                    .orElseThrow();
            assertThat(passwordEncoder.matches("123456", admin.getPasswordHash())).isTrue();

            assertThat(mongoTemplate.findAll(Account.class)).hasSize(1);
            assertThat(mongoTemplate.findAll(Club.class)).isEmpty();
        } finally {
            context.close();
        }
    }

    @Test
    void theDevelopmentProfileAlsoSeedsClubsOfficersAndStudents() {
        ConfigurableApplicationContext context = boot("seeding-test-development", "development");
        try {
            MongoTemplate mongoTemplate = context.getBean(MongoTemplate.class);

            assertThat(mongoTemplate.findAll(Club.class)).hasSize(3);
            java.util.List<Account> accounts = mongoTemplate.findAll(Account.class);
            assertThat(accounts)
                    .extracting(Account::getEmail)
                    .contains("admin@demo.campushub", "officer@demo.campushub", "student@demo.campushub");
            assertThat(accounts).hasSizeGreaterThanOrEqualTo(6);
        } finally {
            context.close();
        }
    }

    private static ConfigurableApplicationContext boot(String databaseName, String profile) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(CampusHubApplication.class)
                .web(WebApplicationType.NONE)
                .properties("MONGODB_URI=" + MONGO_DB.getConnectionString() + "/" + databaseName)
                .properties("SESSION_SECRET=seeding-test-session-secret")
                .properties("CHECKIN_HMAC_SECRET=seeding-test-hmac-secret");
        if (profile != null) {
            builder.profiles(profile);
        }
        return builder.run();
    }
}
