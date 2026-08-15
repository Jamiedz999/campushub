package com.campushub;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.club.domain.Club;
import com.campushub.event.EventModule.AttendanceMethod;
import com.campushub.event.domain.AttendanceEntry;
import com.campushub.event.domain.EnrolledEntry;
import com.campushub.event.domain.EnrollmentVia;
import com.campushub.event.domain.Event;
import com.campushub.identityaccess.domain.Account;
import com.campushub.venue.domain.Venue;
import com.campushub.venue.domain.VenueDay;
import java.time.Instant;
import java.util.List;
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

            assertThat(mongoTemplate.findAll(Club.class)).hasSize(4);
            List<Account> accounts = mongoTemplate.findAll(Account.class);
            assertThat(accounts)
                    .extracting(Account::getEmail)
                    .contains("admin@demo.campushub", "officer@demo.campushub", "student@demo.campushub");
            assertThat(accounts).hasSizeGreaterThanOrEqualTo(6);
        } finally {
            context.close();
        }
    }

    // Issue #12's "legible in thirty seconds" clause, asserted rather than eyeballed. A stranger who
    // signs in should meet a contended Event, a finished one with real attendance numbers behind the
    // dashboard, and a Venue whose day is not empty — none of which a single empty published Event
    // shows. Each shape is asserted by its meaning, not by title, so the seed can be rewritten
    // without rewriting this test.
    @Test
    void theDevelopmentProfileSeedsASceneWithAQueueAttendanceAndVenueBookings() {
        ConfigurableApplicationContext context = boot("seeding-test-demo-scene", "development");
        try {
            MongoTemplate mongoTemplate = context.getBean(MongoTemplate.class);
            List<Event> events = mongoTemplate.findAll(Event.class);
            Instant now = Instant.now();

            Event contended = events.stream()
                    .filter(event -> !event.getWaitlist().isEmpty())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no seeded Event has anyone queued"));
            assertThat(contended.getEnrolled()).hasSize(contended.getCapacity());
            assertThat(contended.getStartsAt()).isAfter(now);

            Event finished = events.stream()
                    .filter(event -> event.getEndsAt().isBefore(now))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no seeded Event has already happened"));
            assertThat(finished.getAttendance())
                    .extracting(AttendanceEntry::method)
                    .contains(AttendanceMethod.SCANNED, AttendanceMethod.MANUAL);
            // The promotion is the product's central claim, so the finished Event carries one that has
            // already happened: a Seat held by a Student who did not win it directly, and the counters
            // that remember a queue which is empty again by the end.
            assertThat(finished.getEnrolled())
                    .extracting(EnrolledEntry::via)
                    .contains(EnrollmentVia.PROMOTED);
            assertThat(finished.getPromotedCount()).isPositive();
            assertThat(finished.getEverQueuedCount()).isPositive();
            // A rate worth reading: some enrolled Student did not turn up, so the dashboard shows a
            // number rather than a flat 100%.
            assertThat(finished.getAttendance().size()).isLessThan(finished.getEnrolled().size());

            assertThat(mongoTemplate.findAll(Venue.class)).isNotEmpty();
            assertThat(mongoTemplate.findAll(VenueDay.class))
                    .flatExtracting(VenueDay::getBookings)
                    .hasSizeGreaterThanOrEqualTo(2);
        } finally {
            context.close();
        }
    }

    // The profile a public deployment actually runs, and the reason it exists: it seeds exactly what
    // "development" seeds, and it carries none of the developer conveniences that profile also carries —
    // the check-in HMAC fallback among them, which SecretsStartupIntegrationTest holds to the same line.
    @Test
    void theDemoProfileSeedsTheSameDataAsTheDevelopmentProfile() {
        ConfigurableApplicationContext context = boot("seeding-test-demo-profile", "demo");
        try {
            MongoTemplate mongoTemplate = context.getBean(MongoTemplate.class);

            assertThat(mongoTemplate.findAll(Club.class)).hasSize(4);
            assertThat(mongoTemplate.findAll(Account.class))
                    .extracting(Account::getEmail)
                    .contains("admin@demo.campushub", "officer@demo.campushub", "student@demo.campushub");
            assertThat(mongoTemplate.findAll(Event.class)).isNotEmpty();
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
