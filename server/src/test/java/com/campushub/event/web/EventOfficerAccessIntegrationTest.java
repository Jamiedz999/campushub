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

// End-to-end proof of Issue #3's headline authorization acceptance criterion: an Officer of one Club
// gets 404 NOT_FOUND, never 403, for another Club's Draft — because the query is scoped by the caller's
// Club grants rather than loaded and then checked. See
// docs/adr/08-define-roles-and-resource-authorization.md.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EventOfficerAccessIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/event-officer-access-test");
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

    private String clubAId;
    private String clubBId;
    private String officerAEmail;
    private String officerBEmail;
    private String studentEmail;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        clubAId = clubModule.createClub("Club A");
        clubBId = clubModule.createClub("Club B");

        String suffix = UUID.randomUUID().toString();
        officerAEmail = "officer-a-" + suffix + "@event-officer-access-test.campushub";
        officerBEmail = "officer-b-" + suffix + "@event-officer-access-test.campushub";
        studentEmail = "student-" + suffix + "@event-officer-access-test.campushub";
        adminEmail = "admin-" + suffix + "@event-officer-access-test.campushub";

        String hash = passwordEncoder.encode(PASSWORD);
        Account officerA = accountRepository.insert(new Account(officerAEmail, hash, "Officer A", SystemRole.STUDENT));
        clubModule.grantOfficer(clubAId, officerA.getId());
        Account officerB = accountRepository.insert(new Account(officerBEmail, hash, "Officer B", SystemRole.STUDENT));
        clubModule.grantOfficer(clubBId, officerB.getId());
        accountRepository.insert(new Account(studentEmail, hash, "Student", SystemRole.STUDENT));
        accountRepository.insert(new Account(adminEmail, hash, "Admin", SystemRole.UNIVERSITY_ADMIN));
    }

    @Test
    void anOfficerOfClubACreatesEditsAndPublishesTheirOwnDraft() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);

        HttpResponse<String> created = officerA.createDraft(clubAId);
        assertThat(created.statusCode()).isEqualTo(201);
        String eventId = extractId(created.body());

        HttpResponse<String> edited = officerA.patch(
                "/events/" + eventId,
                "{\"title\":\"Updated title\",\"capacity\":10}");
        assertThat(edited.statusCode()).isEqualTo(200);
        assertThat(edited.body()).contains("Updated title");

        HttpResponse<String> published = officerA.post("/events/" + eventId + "/publication", "");
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(published.body()).contains("\"status\":\"PUBLISHED\"");
    }

    @Test
    void anOfficerOfClubBReceives404NotFoundNeverForbiddenForClubAsDraft() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        HttpResponse<String> created = officerA.createDraft(clubAId);
        String eventId = extractId(created.body());

        Session officerB = Session.signIn(port, officerBEmail, PASSWORD);
        HttpResponse<String> response = officerB.get("/events/" + eventId);

        assertNotFound(response);
    }

    @Test
    void anOfficerOfClubBCannotEditClubAsDraft() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officerA.createDraft(clubAId).body());

        Session officerB = Session.signIn(port, officerBEmail, PASSWORD);
        HttpResponse<String> response = officerB.patch("/events/" + eventId, "{\"title\":\"Hijacked\"}");

        assertNotFound(response);
    }

    @Test
    void anOfficerOfClubBCannotPublishClubAsDraft() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officerA.createDraft(clubAId).body());

        Session officerB = Session.signIn(port, officerBEmail, PASSWORD);
        HttpResponse<String> response = officerB.post("/events/" + eventId + "/publication", "");

        assertNotFound(response);
    }

    @Test
    void aStudentWithNoGrantsCannotCreateAnEvent() throws Exception {
        Session student = Session.signIn(port, studentEmail, PASSWORD);

        HttpResponse<String> response = student.createDraft(clubAId);

        assertNotFound(response);
    }

    @Test
    void theOwningOfficerCancelsTheirPublishedEvent() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officerA.createDraft(clubAId).body());
        officerA.post("/events/" + eventId + "/publication", "");

        HttpResponse<String> cancelled = officerA.post("/events/" + eventId + "/cancellation", "");

        assertThat(cancelled.statusCode()).isEqualTo(204);
    }

    @Test
    void anOfficerOfAnotherClubCannotCancelIt() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officerA.createDraft(clubAId).body());
        officerA.post("/events/" + eventId + "/publication", "");

        Session officerB = Session.signIn(port, officerBEmail, PASSWORD);
        HttpResponse<String> response = officerB.post("/events/" + eventId + "/cancellation", "");

        assertNotFound(response);
    }

    @Test
    void aUniversityAdminCancelsAnyClubsEventThroughTheSameUnscopedRoute() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officerA.createDraft(clubAId).body());
        officerA.post("/events/" + eventId + "/publication", "");

        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        HttpResponse<String> cancelled = admin.post("/events/" + eventId + "/cancellation", "");

        assertThat(cancelled.statusCode()).isEqualTo(204);
    }

    // "cancel only" is a boundary, not a summary: the cancellation route above lets a University Admin
    // clear a room, and every other route on the same Event refuses them, because authoring and running
    // an Event is the Club's business. See docs/adr/08-define-roles-and-resource-authorization.md.
    @Test
    void aUniversityAdminCannotCreateEditPublishOrReadAnEventTheyMayCancel() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officerA.createDraft(clubAId).body());

        Session admin = Session.signIn(port, adminEmail, PASSWORD);

        assertNotFound(admin.createDraft(clubAId));
        assertNotFound(admin.get("/events/" + eventId));
        assertNotFound(admin.patch("/events/" + eventId, "{\"title\":\"Admin edit\",\"capacity\":10}"));
        assertNotFound(admin.put("/events/" + eventId + "/registration-form", "{\"fields\":[]}"));
        assertNotFound(admin.post("/events/" + eventId + "/publication", ""));
        assertThat(officerA.get("/events/" + eventId).body()).contains("\"status\":\"DRAFT\"");
    }

    // A Club Officer's own routes still refuse a Student who holds no grant anywhere.
    @Test
    void aStudentWithNoGrantsCannotReadEditOrPublishADraft() throws Exception {
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officerA.createDraft(clubAId).body());

        Session student = Session.signIn(port, studentEmail, PASSWORD);

        assertNotFound(student.get("/events/" + eventId));
        assertNotFound(student.patch("/events/" + eventId, "{\"title\":\"Student edit\",\"capacity\":10}"));
        assertNotFound(student.put("/events/" + eventId + "/registration-form", "{\"fields\":[]}"));
        assertNotFound(student.post("/events/" + eventId + "/publication", ""));
        assertNotFound(student.post("/events/" + eventId + "/cancellation", ""));
    }

    // 404 carrying NOT_FOUND, never 403: a refusal that admits the resource exists is the leak the
    // scoped-query rule exists to prevent.
    private static void assertNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
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

        HttpResponse<String> createDraft(String clubId) throws Exception {
            // Far-future, real-clock-relative: this test boots the real application, whose Clock bean
            // is the system clock (not a fixed test clock), so these must never lapse into the past.
            String body =
                    "{\"title\":\"Robotics Night\",\"description\":\"build things\","
                            + "\"registrationOpensAt\":\"2099-03-01T00:00:00Z\","
                            + "\"registrationClosesAt\":\"2099-03-10T00:00:00Z\","
                            + "\"startsAt\":\"2099-03-20T00:00:00Z\","
                            + "\"endsAt\":\"2099-03-20T02:00:00Z\","
                            + "\"capacity\":5}";
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

        HttpResponse<String> patch(String path, String jsonBody) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                    .method("PATCH", BodyPublishers.ofString(jsonBody))
                    .header("Content-Type", "application/json");
            csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> put(String path, String jsonBody) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                    .PUT(BodyPublishers.ofString(jsonBody))
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
