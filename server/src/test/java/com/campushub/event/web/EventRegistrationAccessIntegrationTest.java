package com.campushub.event.web;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// End-to-end proof that Taking a Seat is wired all the way through the real HTTP stack: routing,
// authentication, CSRF, and problem+json with the matching `code`. The atomicity claim itself is proven
// against real Mongo by EventRepositorySeatLedgerIntegrationTest's concurrency test — this only proves
// the wiring around that guarded write is correct. See docs/adr/04-define-registration-capacity-and-waitlist.md.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EventRegistrationAccessIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/event-registration-access-test");
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

    private String clubId;
    private String officerEmail;
    private String studentAEmail;
    private String studentBEmail;

    @BeforeEach
    void setUp() {
        clubId = clubModule.createClub("Robotics Club");

        String suffix = UUID.randomUUID().toString();
        officerEmail = "officer-" + suffix + "@event-registration-access-test.campushub";
        studentAEmail = "student-a-" + suffix + "@event-registration-access-test.campushub";
        studentBEmail = "student-b-" + suffix + "@event-registration-access-test.campushub";

        String hash = passwordEncoder.encode(PASSWORD);
        Account officer = accountRepository.insert(new Account(officerEmail, hash, "Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(clubId, officer.getId());
        accountRepository.insert(new Account(studentAEmail, hash, "Student A", SystemRole.STUDENT));
        accountRepository.insert(new Account(studentBEmail, hash, "Student B", SystemRole.STUDENT));
    }

    @Test
    void aStudentRegistersForAPublishedEventAndSeesItInMyEvents() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEvent(officer, 5);

        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        HttpResponse<String> registered = studentA.post("/events/" + eventId + "/registration", "");

        assertThat(registered.statusCode()).isEqualTo(200);
        assertThat(registered.body()).contains("\"enrolled\":true").contains("\"enrolledCount\":1");

        HttpResponse<String> mine = studentA.get("/events/mine");
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.body()).contains(eventId);
    }

    @Test
    void aDoubleClickedRegistrationIsRefusedAsAlreadyEnrolled() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEvent(officer, 5);

        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        studentA.post("/events/" + eventId + "/registration", "");
        HttpResponse<String> secondAttempt = studentA.post("/events/" + eventId + "/registration", "");

        assertThat(secondAttempt.statusCode()).isEqualTo(409);
        assertThat(secondAttempt.body()).contains("\"code\":\"ALREADY_ENROLLED\"");
    }

    @Test
    void aLosingWriterAgainstAFullEventIsRefusedAsEventFull() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEvent(officer, 1);

        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        HttpResponse<String> winner = studentA.post("/events/" + eventId + "/registration", "");
        assertThat(winner.statusCode()).isEqualTo(200);

        Session studentB = Session.signIn(port, studentBEmail, PASSWORD);
        HttpResponse<String> loser = studentB.post("/events/" + eventId + "/registration", "");

        assertThat(loser.statusCode()).isEqualTo(409);
        assertThat(loser.body()).contains("\"code\":\"EVENT_FULL\"");
    }

    @Test
    void aStudentCannotSeeOrRegisterForAStillUnpublishedDraft() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = extractId(officer.createDraft(clubId, 5).body());

        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        HttpResponse<String> view = studentA.get("/events/" + eventId + "/registration");
        HttpResponse<String> attempt = studentA.post("/events/" + eventId + "/registration", "");

        assertThat(view.statusCode()).isEqualTo(404);
        assertThat(attempt.statusCode()).isEqualTo(404);
    }

    private String publishEvent(Session officer, int capacity) throws Exception {
        HttpResponse<String> created = officer.createDraft(clubId, capacity);
        String eventId = extractId(created.body());
        officer.post("/events/" + eventId + "/publication", "");
        return eventId;
    }

    private static String extractId(String body) {
        int start = body.indexOf("\"id\":\"") + 6;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    /** A same-origin, cookie-carrying HTTP session signed in as one account — mirrors a real browser tab. */
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

        HttpResponse<String> createDraft(String clubId, int capacity) throws Exception {
            // Real-clock-relative: this test boots the real application, whose Clock bean is the system
            // clock (not a fixed test clock). Registration must already be open (opensAt in the past)
            // while startsAt/endsAt stay far enough in the future that the freeze never engages.
            String body = "{\"title\":\"Robotics Night\",\"description\":\"build things\","
                    + "\"registrationOpensAt\":\"2020-01-01T00:00:00Z\","
                    + "\"registrationClosesAt\":\"2099-03-10T00:00:00Z\","
                    + "\"startsAt\":\"2099-03-20T00:00:00Z\","
                    + "\"endsAt\":\"2099-03-20T02:00:00Z\","
                    + "\"capacity\":" + capacity + "}";
            return post("/clubs/" + clubId + "/events", body);
        }

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url(path))).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String jsonBody) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                    .POST(BodyPublishers.ofString(jsonBody))
                    .header("Content-Type", "application/json");
            csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private Optional<String> csrfToken() {
            return cookieManager.getCookieStore().getCookies().stream()
                    .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst();
        }

        private String url(String path) {
            return "http://localhost:" + port + "/api" + path;
        }
    }
}
