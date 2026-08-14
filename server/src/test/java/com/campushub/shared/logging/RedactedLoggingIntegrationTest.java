package com.campushub.shared.logging;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * The acceptance criterion run rather than asserted: a full journey against the real stack, and a grep
 * of what it wrote to the log.
 *
 * <p>Logging is turned up to DEBUG for the application and for Spring's web layer on purpose. At the
 * default level this journey writes almost nothing, and a grep of almost nothing finds no identifier
 * whether the redaction works or not. DEBUG is where Spring names the URI it is dispatching — which
 * carries Event ids and, on one route, a Student id — so it is the level at which the grep has
 * something to fail on.
 *
 * <p>{@code aLineWrittenWithAnIdentifierInItComesOutMasked} is the other half. Without it, a passing
 * grep would only prove that nothing tried to log an identifier today; with it, the grep is proof that
 * the masking is wired into the appender and would catch the line somebody writes tomorrow.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.level.com.campushub=DEBUG", "logging.level.org.springframework.web=DEBUG"})
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class RedactedLoggingIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(RedactedLoggingIntegrationTest.class);
    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String SHIRT_FIELD_ID = "507f1f77bcf86cd799439011";
    private static final String ANSWER = "M";

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/redacted-logging-test");
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
    private String studentEmail;
    private String studentId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        clubId = clubModule.createClub("Robotics Society " + suffix);
        officerEmail = "officer-" + suffix + "@redacted-logging-test.campushub";
        studentEmail = "student-" + suffix + "@redacted-logging-test.campushub";

        String hash = passwordEncoder.encode(PASSWORD);
        Account officer = accountRepository.insert(new Account(officerEmail, hash, "Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(clubId, officer.getId());
        studentId = accountRepository
                .insert(new Account(studentEmail, hash, "Ada Lovelace", SystemRole.STUDENT))
                .getId();
    }

    @Test
    void aFullJourneyLeavesNoStudentIdentifierInTheLogOutput(CapturedOutput output) throws Exception {
        String roster = runTheJourney();

        // The journey first, so that a grep over a journey that quietly 404'd its way through cannot
        // pass for a clean one: the Student is on the Roster, scanned, which only a real run produces.
        assertThat(roster).contains("\"method\":\"SCANNED\"");
        assertThat(output.getAll())
                .doesNotContain(studentEmail)
                .doesNotContain(studentId)
                .doesNotContain(officerEmail);
    }

    @Test
    void aLineWrittenWithAnIdentifierInItComesOutMasked(CapturedOutput output) {
        LOG.info("enrolled {} ({}) on the last Seat", studentEmail, studentId);

        assertThat(output.getAll())
                .doesNotContain(studentEmail)
                .doesNotContain(studentId)
                .contains("enrolled [redacted-email] ([redacted-id]) on the last Seat");
    }

    @Test
    void aStackTraceCarryingAnIdentifierComesOutMaskedToo(CapturedOutput output) {
        LOG.warn("the write failed", new IllegalStateException("no Seat for " + studentEmail));

        assertThat(output.getAll()).doesNotContain(studentEmail).contains("no Seat for [redacted-email]");
    }

    @Test
    void everyLineAJourneyWritesCarriesTheCorrelationIdTheCallerSent(CapturedOutput output) throws Exception {
        Session student = Session.signIn(port, studentEmail, PASSWORD);

        student.getWithCorrelationId("/events", "trace-me-0192837465");

        assertThat(output.getAll()).contains("[trace-me-0192837465]");
    }

    // The response is the only place a correlation id is any use to the person reporting a problem,
    // and the responses worth reporting are the ones that went wrong. A 401 arrives from the security
    // filter chain, before any controller — which is why the filter sets the header ahead of the chain
    // rather than after it.
    @Test
    void aRefusedRequestCarriesACorrelationIdBackJustLikeASuccessfulOne() throws Exception {
        Session anonymous = new Session(port);

        HttpResponse<String> refused = anonymous.get("/auth/me");

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(refused.headers().firstValue("X-Correlation-Id")).isPresent();
    }

    @Test
    void aLineWrittenOutsideAnyRequestSaysSoRatherThanShowingAnEmptySlot(CapturedOutput output) {
        LOG.info("started up");

        assertThat(output.getAll()).contains("[no-request]");
    }

    // Publish with a form, answer it, take a Seat, scan in at the door, read the roster, withdraw. Every
    // route on the journey handles a Student, and every one of them is a chance to log one.
    private String runTheJourney() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = officer.createDraft(clubId);
        officer.put(
                "/events/" + eventId + "/registration-form",
                "{\"fields\":[{\"type\":\"SINGLE_CHOICE\",\"fieldId\":\"" + SHIRT_FIELD_ID
                        + "\",\"label\":\"T-shirt\",\"helpText\":null,\"required\":true,"
                        + "\"options\":[\"S\",\"M\",\"L\"]}]}");
        officer.post("/events/" + eventId + "/publication", "");

        Session student = Session.signIn(port, studentEmail, PASSWORD);
        student.get("/events");
        student.post(
                "/events/" + eventId + "/registration",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"" + ANSWER + "\"}}");
        student.get("/events/mine");

        String doorCode = officer.get("/events/" + eventId + "/door-code").body();
        student.post(
                "/events/" + eventId + "/attendance",
                "{\"token\":\"" + extractString(doorCode, "token") + "\"}");
        String roster = officer.get("/events/" + eventId + "/attendance").body();
        officer.put("/events/" + eventId + "/attendance/" + studentId, "");
        officer.get("/events/" + eventId + "/registration-answers");
        officer.get("/events/" + eventId + "/registration-answers/csv");
        student.delete("/events/" + eventId + "/registration");
        return roster;
    }

    private static String extractString(String body, String field) {
        int start = body.indexOf("\"" + field + "\":\"") + field.length() + 4;
        return body.substring(start, body.indexOf('"', start));
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

        String createDraft(String clubId) throws Exception {
            // Ten minutes out: registration is open and the check-in window, which opens fifteen minutes
            // before startsAt, is open with it — the overlap the door actually runs in.
            Instant startsAt = Instant.now().plus(Duration.ofMinutes(10));
            String body = "{\"title\":\"Robotics Night\",\"description\":\"build things\","
                    + "\"registrationOpensAt\":\"2020-01-01T00:00:00Z\","
                    + "\"registrationClosesAt\":\"" + startsAt + "\","
                    + "\"startsAt\":\"" + startsAt + "\","
                    + "\"endsAt\":\"" + startsAt.plus(Duration.ofHours(1)) + "\","
                    + "\"capacity\":5}";
            String created = post("/clubs/" + clubId + "/events", body).body();
            return extractString(created, "id");
        }

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url(path))).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> getWithCorrelationId(String path, String correlationId) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                    .GET()
                    .header("X-Correlation-Id", correlationId)
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String jsonBody) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(url(path)))
                    .POST(BodyPublishers.ofString(jsonBody)));
        }

        HttpResponse<String> put(String path, String jsonBody) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(url(path)))
                    .PUT(BodyPublishers.ofString(jsonBody)));
        }

        HttpResponse<String> delete(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(url(path))).DELETE());
        }

        private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
            builder.header("Content-Type", "application/json");
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
