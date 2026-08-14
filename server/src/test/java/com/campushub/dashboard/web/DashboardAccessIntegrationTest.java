package com.campushub.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.club.ClubModule;
import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.identityaccess.persistence.AccountRepository;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// The dashboard end to end over real HTTP: which of the ADR's two views each role gets, and the
// boundary that a Club Officer's numbers stop at their own Clubs. See
// docs/adr/09-define-attendance-dashboard.md and docs/adr/08-define-roles-and-resource-authorization.md.
//
// The finished Events are written straight into Mongo rather than created and published over the API,
// because the population is Events whose endsAt has passed and there is no way to reach that state
// through the lifecycle inside a test — publishing takes a moment, and time only moves one way.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DashboardAccessIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/dashboard-access-test");
        registry.add("campushub.security.session-secret", () -> "test-session-secret");
        registry.add("campushub.checkin.hmac-secret", () -> "test-checkin-hmac-secret");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ClubModule clubModule;

    @Autowired
    private MongoTemplate mongoTemplate;

    private String clubId;
    private String otherClubId;
    private String officerEmail;
    private String otherOfficerEmail;
    private String studentEmail;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        clubId = clubModule.createClub("Robotics Society " + suffix);
        otherClubId = clubModule.createClub("Choir " + suffix);
        officerEmail = "officer-" + suffix + "@dashboard-access-test.campushub";
        otherOfficerEmail = "other-officer-" + suffix + "@dashboard-access-test.campushub";
        studentEmail = "student-" + suffix + "@dashboard-access-test.campushub";
        adminEmail = "admin-" + suffix + "@dashboard-access-test.campushub";

        String hash = passwordEncoder.encode(PASSWORD);
        Account officer = accountRepository.insert(new Account(officerEmail, hash, "Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(clubId, officer.getId());
        Account otherOfficer =
                accountRepository.insert(new Account(otherOfficerEmail, hash, "Other Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(otherClubId, otherOfficer.getId());
        accountRepository.insert(new Account(studentEmail, hash, "Student", SystemRole.STUDENT));
        accountRepository.insert(
                new Account(adminEmail, hash, "Registrar", SystemRole.UNIVERSITY_ADMIN));

        // One finished Event per Club: 40 seats, 30 enrolled, 24 attended of which 4 were overrides.
        insertFinishedEvent(clubId, "Robotics talk");
        insertFinishedEvent(otherClubId, "Choir practice");
    }

    @Test
    void anOfficerGetsTheirOwnClubsNumbersAndNoOtherClubsAppearInThem() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);

        HttpResponse<String> response = officer.get("/dashboard");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"scope\":\"CLUB\"").contains("Robotics Society");
        assertThat(response.body()).doesNotContain("Choir");
        assertThat(response.body()).contains("\"eventsRun\":1");
    }

    @Test
    void anOfficerAskingForAnotherClubsMetricsFindsNothing() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);

        HttpResponse<String> refused = officer.get("/dashboard?clubId=" + otherClubId);

        assertNotFound(refused);
    }

    @Test
    void aStudentWhoOfficersNothingHasNoDashboard() throws Exception {
        Session student = Session.signIn(port, studentEmail, PASSWORD);

        assertNotFound(student.get("/dashboard"));
    }

    @Test
    void signingInIsRequiredLikeEveryOtherApiRoute() throws Exception {
        Session anonymous = new Session(port);

        assertThat(anonymous.get("/dashboard").statusCode()).isEqualTo(401);
    }

    @Test
    void aUniversityAdminSeesEveryClubAndCanStillNarrowToOne() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);

        HttpResponse<String> everyClub = admin.get("/dashboard");
        assertThat(everyClub.statusCode()).isEqualTo(200);
        assertThat(everyClub.body())
                .contains("\"scope\":\"ALL_CLUBS\"")
                .contains("Robotics Society")
                .contains("Choir");

        HttpResponse<String> oneClub = admin.get("/dashboard?clubId=" + otherClubId);
        assertThat(oneClub.statusCode()).isEqualTo(200);
        assertThat(oneClub.body()).contains("\"scope\":\"CLUB\"").doesNotContain("Robotics Society");
    }

    @Test
    void noIndividualStudentOrFormAnswerAppearsInEitherView() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);

        String body = admin.get("/dashboard").body();

        assertThat(body).doesNotContain("studentId").doesNotContain("answers").doesNotContain("student-");
    }

    @Test
    void theRangeIsHonouredAndAnEventOutsideItIsNotCounted() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);

        String body = officer.get("/dashboard?from=2000-01-01T00:00:00Z&to=2000-12-31T00:00:00Z")
                .body();

        assertThat(body).contains("\"eventsRun\":0").contains("\"events\":[]");
    }

    // 404 carrying NOT_FOUND, never 403: a refusal that admits the resource exists is the leak the
    // scoped-query rule exists to prevent.
    private static void assertNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    private void insertFinishedEvent(String owningClubId, String title) {
        Instant ends = Instant.now().minus(Duration.ofDays(3));
        List<Document> attendance = new ArrayList<>();
        IntStream.range(0, 24)
                .forEach(index -> attendance.add(new Document("studentId", "student-" + index)
                        .append("at", Date.from(ends))
                        .append("method", index < 4 ? "MANUAL" : "SCANNED")));
        mongoTemplate
                .getCollection("events")
                .insertOne(new Document("clubId", owningClubId)
                        .append("title", title)
                        .append("status", "PUBLISHED")
                        .append("startsAt", Date.from(ends.minus(Duration.ofHours(2))))
                        .append("endsAt", Date.from(ends))
                        .append("capacity", 40)
                        .append(
                                "enrolled",
                                IntStream.range(0, 30)
                                        .mapToObj(index -> new Document("studentId", "student-" + index)
                                                .append("via", "DIRECT"))
                                        .toList())
                        .append("waitlist", List.of("queued-0", "queued-1"))
                        .append("attendance", attendance)
                        .append("promotedCount", 3)
                        .append("everQueuedCount", 6));
    }

    private static final class Session {

        private final int port;
        private final CookieManager cookieManager = new CookieManager();
        private final HttpClient client;

        private Session(int port) {
            this.port = port;
            this.client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        }

        static Session signIn(int port, String email, String password) throws Exception {
            Session session = new Session(port);
            session.get("/auth/me");
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(session.url("/auth/login")))
                    .POST(BodyPublishers.ofString("email=" + email + "&password=" + password))
                    .header("Content-Type", "application/x-www-form-urlencoded");
            session.csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
            session.client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            session.get("/auth/me");
            return session;
        }

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url(path))).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        private Optional<String> csrfToken() {
            return cookieManager.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                    .map(HttpCookie::getValue)
                    .findFirst();
        }

        private String url(String path) {
            return "http://localhost:" + port + "/api" + path;
        }
    }
}
