package com.campushub.venue.web;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class VenueAccessIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/venue-access-test");
        registry.add("campushub.security.session-secret", () -> "venue-access-session-secret");
        registry.add("campushub.checkin.hmac-secret", () -> "venue-access-hmac-secret");
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
    private String officerAEmail;
    private String officerBEmail;
    private String studentEmail;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        clubAId = clubModule.createClub("Venue Club A " + UUID.randomUUID());
        String clubBId = clubModule.createClub("Venue Club B " + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString();
        officerAEmail = "venue-officer-a-" + suffix + "@campushub.test";
        officerBEmail = "venue-officer-b-" + suffix + "@campushub.test";
        studentEmail = "venue-student-" + suffix + "@campushub.test";
        adminEmail = "venue-admin-" + suffix + "@campushub.test";

        String hash = passwordEncoder.encode(PASSWORD);
        Account officerA = accountRepository.insert(
                new Account(officerAEmail, hash, "Venue Officer A", SystemRole.STUDENT));
        clubModule.grantOfficer(clubAId, officerA.getId());
        Account officerB = accountRepository.insert(
                new Account(officerBEmail, hash, "Venue Officer B", SystemRole.STUDENT));
        clubModule.grantOfficer(clubBId, officerB.getId());
        accountRepository.insert(new Account(studentEmail, hash, "Venue Student", SystemRole.STUDENT));
        accountRepository.insert(new Account(adminEmail, hash, "Venue Admin", SystemRole.UNIVERSITY_ADMIN));
    }

    @Test
    void anAdminManagesVenuesWhileOfficersCanOnlyListThemAndStudentsSeeNothing() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        HttpResponse<String> created = admin.post("/venues", "{\"name\":\"Sports Hall\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        String venueId = extractId(created.body());

        HttpResponse<String> renamed =
                admin.patch("/venues/" + venueId, "{\"name\":\"Main Sports Hall\"}");
        assertThat(renamed.statusCode()).isEqualTo(200);
        assertThat(renamed.body()).contains("Main Sports Hall");

        Session officer = Session.signIn(port, officerAEmail, PASSWORD);
        HttpResponse<String> listed = officer.get("/venues?page=0&size=100");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).contains(venueId, "Main Sports Hall");
        assertNotFound(officer.post("/venues", "{\"name\":\"Not allowed\"}"));

        Session student = Session.signIn(port, studentEmail, PASSWORD);
        assertNotFound(student.get("/venues"));
        assertNotFound(student.post("/venues", "{\"name\":\"Not allowed\"}"));
    }

    @Test
    void anOfficerBooksReadsAndIdempotentlyReleasesTheirEventsVenue() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        String venueId = extractId(admin.post("/venues", "{\"name\":\"Timeline Hall\"}").body());
        Session officer = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officer.createDraft(clubAId, "Venue Event").body());

        HttpResponse<String> booked = officer.put("/events/" + eventId + "/slot", slot(venueId));
        assertThat(booked.statusCode()).isEqualTo(204);
        HttpResponse<String> day = officer.get("/venues/" + venueId + "/days/2099-03-20");
        assertThat(day.statusCode()).isEqualTo(200);
        assertThat(day.body()).contains(eventId, "\"startMinute\":600", "\"endMinute\":660");

        Session otherOfficer = Session.signIn(port, officerBEmail, PASSWORD);
        assertNotFound(otherOfficer.put("/events/" + eventId + "/slot", slot(venueId)));
        Session student = Session.signIn(port, studentEmail, PASSWORD);
        assertNotFound(student.get("/venues/" + venueId + "/days/2099-03-20"));

        assertThat(officer.delete("/events/" + eventId + "/slot").statusCode())
                .isEqualTo(204);
        assertThat(officer.delete("/events/" + eventId + "/slot").statusCode())
                .isEqualTo(204);
        assertThat(officer.get("/events/" + eventId).body()).contains("\"venueId\":null");
        assertThat(officer.get("/venues/" + venueId + "/days/2099-03-20").body())
                .doesNotContain(eventId);
    }

    @Test
    void anAdminManagesVenueRecordsButCannotBookOrReleaseAnEventsSlot() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        String venueId = extractId(admin.post("/venues", "{\"name\":\"Admin Boundary Hall\"}").body());
        Session officer = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officer.createDraft(clubAId, "Officer-owned Event").body());

        assertNotFound(admin.put("/events/" + eventId + "/slot", slot(venueId)));
        assertThat(officer.put("/events/" + eventId + "/slot", slot(venueId)).statusCode())
                .isEqualTo(204);
        assertNotFound(admin.delete("/events/" + eventId + "/slot"));
        assertThat(officer.get("/events/" + eventId).body()).contains("\"venueId\":\"" + venueId + "\"");
    }

    @Test
    void aLostOverlappingBookingRaceReturnsTheStableSlotTakenProblem() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        String venueId = extractId(admin.post("/venues", "{\"name\":\"Busy Hall\"}").body());
        Session officer = Session.signIn(port, officerAEmail, PASSWORD);
        String firstEvent = extractId(officer.createDraft(clubAId, "First Event").body());
        String secondEvent = extractId(officer.createDraft(clubAId, "Second Event").body());
        assertThat(officer.put("/events/" + firstEvent + "/slot", slot(venueId)).statusCode())
                .isEqualTo(204);

        HttpResponse<String> refused =
                officer.put("/events/" + secondEvent + "/slot", slot(venueId));

        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).contains("\"code\":\"SLOT_TAKEN\"");
        assertThat(officer.get("/events/" + secondEvent).body())
                .contains("\"venueId\":null", "\"startsAt\":\"2099-03-20T10:00:00Z\"");
    }

    private static String slot(String venueId) {
        return "{\"venueId\":\"" + venueId + "\","
                + "\"startsAt\":\"2099-03-20T10:00:00Z\","
                + "\"endsAt\":\"2099-03-20T11:00:00Z\"}";
    }

    // The Venue Slot row of the permission matrix, standing on its own rather than as three assertions
    // inside the Officer's happy path: a Slot is booked against an Event, so the Event's Club is what
    // decides, and an Officer of another Club is refused exactly as a Student is. See
    // docs/adr/08-define-roles-and-resource-authorization.md.
    @Test
    void neitherAnOfficerOfAnotherClubNorAStudentCanBookOrReleaseAnEventsSlot() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        String venueId = extractId(admin.post("/venues", "{\"name\":\"Cross-club Hall\"}").body());
        Session officerA = Session.signIn(port, officerAEmail, PASSWORD);
        String eventId = extractId(officerA.createDraft(clubAId, "Club A's Event").body());

        Session officerB = Session.signIn(port, officerBEmail, PASSWORD);
        Session student = Session.signIn(port, studentEmail, PASSWORD);

        assertNotFound(officerB.put("/events/" + eventId + "/slot", slot(venueId)));
        assertNotFound(officerB.delete("/events/" + eventId + "/slot"));
        assertNotFound(student.put("/events/" + eventId + "/slot", slot(venueId)));
        assertNotFound(student.delete("/events/" + eventId + "/slot"));
        assertNotFound(student.get("/venues/" + venueId + "/days/2099-03-20"));
        // The owning Officer still books it, so the refusals above are a boundary, not a broken route.
        assertThat(officerA.put("/events/" + eventId + "/slot", slot(venueId)).statusCode())
                .isEqualTo(204);
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

        HttpResponse<String> createDraft(String clubId, String title) throws Exception {
            String body = "{\"title\":\"" + title + "\",\"description\":\"Venue test\","
                    + "\"registrationOpensAt\":\"2099-03-01T00:00:00Z\","
                    + "\"registrationClosesAt\":\"2099-03-10T00:00:00Z\","
                    + "\"startsAt\":\"2099-03-20T10:00:00Z\","
                    + "\"endsAt\":\"2099-03-20T11:00:00Z\","
                    + "\"capacity\":20}";
            return post("/clubs/" + clubId + "/events", body);
        }

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url(path))).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String body) throws Exception {
            return sendWithCsrf("POST", path, body);
        }

        HttpResponse<String> put(String path, String body) throws Exception {
            return sendWithCsrf("PUT", path, body);
        }

        HttpResponse<String> patch(String path, String body) throws Exception {
            return sendWithCsrf("PATCH", path, body);
        }

        HttpResponse<String> delete(String path) throws Exception {
            return sendWithCsrf("DELETE", path, "");
        }

        private HttpResponse<String> sendWithCsrf(String method, String path, String body) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                    .method(method, BodyPublishers.ofString(body))
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
