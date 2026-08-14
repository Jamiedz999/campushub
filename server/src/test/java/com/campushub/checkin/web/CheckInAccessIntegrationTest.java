package com.campushub.checkin.web;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// The whole door, end to end over real HTTP against real Mongo: an Officer projects a code, a Student
// scans it from their own session, and every refusal arrives as problem+json carrying the `code` the
// phone switches on. See docs/adr/07-define-qr-checkin-and-anti-fraud.md.
//
// Events here start ten minutes from now on the real system clock, which is the only arrangement in
// which registration is still open (startsAt is in the future) and the check-in window is already open
// (it opened fifteen minutes before startsAt) — the overlap the door actually runs in.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CheckInAccessIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/checkin-access-test");
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
    private String otherOfficerEmail;
    private String studentAEmail;
    private String studentBEmail;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        clubId = clubModule.createClub("Mountaineering Club " + suffix);
        String otherClubId = clubModule.createClub("Chess Club " + suffix);
        officerEmail = "officer-" + suffix + "@checkin-access-test.campushub";
        otherOfficerEmail = "other-officer-" + suffix + "@checkin-access-test.campushub";
        studentAEmail = "student-a-" + suffix + "@checkin-access-test.campushub";
        studentBEmail = "student-b-" + suffix + "@checkin-access-test.campushub";
        adminEmail = "admin-" + suffix + "@checkin-access-test.campushub";

        String hash = passwordEncoder.encode(PASSWORD);
        Account officer = accountRepository.insert(new Account(officerEmail, hash, "Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(clubId, officer.getId());
        Account otherOfficer =
                accountRepository.insert(new Account(otherOfficerEmail, hash, "Other Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(otherClubId, otherOfficer.getId());
        accountRepository.insert(new Account(studentAEmail, hash, "Student A", SystemRole.STUDENT));
        accountRepository.insert(new Account(studentBEmail, hash, "Student B", SystemRole.STUDENT));
        accountRepository.insert(new Account(adminEmail, hash, "Campus Admin", SystemRole.UNIVERSITY_ADMIN));
    }

    @Test
    void aStudentScansTheDoorCodeAndIsMarkedPresent() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        student.post("/events/" + eventId + "/registration", "");

        HttpResponse<String> door = officer.get("/events/" + eventId + "/door-code");
        assertThat(door.statusCode()).isEqualTo(200);
        assertThat(door.body()).contains("\"checkInOpen\":true");

        HttpResponse<String> scan = student.post("/events/" + eventId + "/attendance", scan(door.body()));

        assertThat(scan.statusCode()).isEqualTo(200);
        assertThat(scan.body()).contains("\"method\":\"SCANNED\"");
    }

    @Test
    void aSecondScanReportsAlreadyCheckedInAndCreatesNoSecondRecord() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        student.post("/events/" + eventId + "/registration", "");
        String token = scan(officer.get("/events/" + eventId + "/door-code").body());
        student.post("/events/" + eventId + "/attendance", token);

        HttpResponse<String> again = student.post("/events/" + eventId + "/attendance", token);

        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(again.body()).contains("\"code\":\"ALREADY_CHECKED_IN\"").contains("\"at\":");
        // One record, not two: the roster names this Student once, scanned.
        assertThat(scannedRows(officer.get("/events/" + eventId + "/attendance").body())).isEqualTo(1);
    }

    @Test
    void aWaitlistedStudentIsToldWhyRatherThanBeingLetIn() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 1);
        Session enrolledStudent = Session.signIn(port, studentAEmail, PASSWORD);
        Session waitlistedStudent = Session.signIn(port, studentBEmail, PASSWORD);
        enrolledStudent.post("/events/" + eventId + "/registration", "");
        waitlistedStudent.post("/events/" + eventId + "/registration", "");

        String token = scan(officer.get("/events/" + eventId + "/door-code").body());
        HttpResponse<String> refused = waitlistedStudent.post("/events/" + eventId + "/attendance", token);

        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).contains("\"code\":\"NOT_ON_ROSTER\"");
    }

    @Test
    void aCodeFromAnotherEventsDoorIsRefused() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);
        String otherEventId = openEvent(officer, 5);
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        student.post("/events/" + eventId + "/registration", "");

        String otherToken = scan(officer.get("/events/" + otherEventId + "/door-code").body());
        HttpResponse<String> refused = student.post("/events/" + eventId + "/attendance", otherToken);

        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).contains("\"code\":\"TOKEN_INVALID\"");
    }

    @Test
    void aTamperedCodeIsRefused() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        student.post("/events/" + eventId + "/registration", "");

        HttpResponse<String> refused =
                student.post("/events/" + eventId + "/attendance", "{\"token\":\"" + eventId + ".1.forged\"}");

        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).contains("\"code\":\"TOKEN_INVALID\"");
    }

    @Test
    void anOfficerOfAnotherClubCannotOpenTheDoorOrReadTheRoster() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);

        Session otherOfficer = Session.signIn(port, otherOfficerEmail, PASSWORD);

        assertThat(otherOfficer.get("/events/" + eventId + "/door-code").statusCode())
                .isEqualTo(404);
        assertThat(otherOfficer.get("/events/" + eventId + "/attendance").statusCode())
                .isEqualTo(404);
    }

    @Test
    void aStudentCannotOpenTheDoorScreenOfAnEventTheyAreAttending() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        student.post("/events/" + eventId + "/registration", "");

        assertThat(student.get("/events/" + eventId + "/door-code").statusCode()).isEqualTo(404);
        assertThat(student.get("/events/" + eventId + "/attendance").statusCode()).isEqualTo(404);
    }

    @Test
    void anOfficerMarksAStudentPresentManuallyAndTheRosterKeepsTheTwoApart() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);
        Session scanningStudent = Session.signIn(port, studentAEmail, PASSWORD);
        Session brokenPhoneStudent = Session.signIn(port, studentBEmail, PASSWORD);
        scanningStudent.post("/events/" + eventId + "/registration", "");
        brokenPhoneStudent.post("/events/" + eventId + "/registration", "");
        scanningStudent.post(
                "/events/" + eventId + "/attendance",
                scan(officer.get("/events/" + eventId + "/door-code").body()));

        String brokenPhoneStudentId = studentIdOf(officer.get("/events/" + eventId + "/attendance").body(), null);
        HttpResponse<String> marked =
                officer.put("/events/" + eventId + "/attendance/" + brokenPhoneStudentId, "");

        assertThat(marked.statusCode()).isEqualTo(204);
        String roster = officer.get("/events/" + eventId + "/attendance").body();
        assertThat(roster).contains("\"method\":\"SCANNED\"").contains("\"method\":\"MANUAL\"");
        assertThat(scannedRows(roster) + manualRows(roster)).isEqualTo(2);
    }

    @Test
    void anOfficerOfAnotherClubCannotMarkAnyonePresent() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        student.post("/events/" + eventId + "/registration", "");
        String studentId = studentIdOf(officer.get("/events/" + eventId + "/attendance").body(), null);

        Session otherOfficer = Session.signIn(port, otherOfficerEmail, PASSWORD);
        HttpResponse<String> refused =
                otherOfficer.put("/events/" + eventId + "/attendance/" + studentId, "");

        assertThat(refused.statusCode()).isEqualTo(404);
        assertThat(officer.get("/events/" + eventId + "/attendance").body()).contains("\"method\":null");
    }

    // The University Admin row of the permission matrix, which no positive test can show: running the
    // door and overriding attendance are the Club's business, and campus-wide authority is not a way in.
    // See docs/adr/08-define-roles-and-resource-authorization.md.
    @Test
    void aUniversityAdminCannotRunTheDoorReadTheRosterOrOverrideAttendance() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = openEvent(officer, 5);
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        student.post("/events/" + eventId + "/registration", "");
        String studentId = studentIdOf(officer.get("/events/" + eventId + "/attendance").body(), null);

        Session admin = Session.signIn(port, adminEmail, PASSWORD);

        assertNotFound(admin.get("/events/" + eventId + "/door-code"));
        assertNotFound(admin.get("/events/" + eventId + "/attendance"));
        assertNotFound(admin.put("/events/" + eventId + "/attendance/" + studentId, ""));
        assertThat(officer.get("/events/" + eventId + "/attendance").body()).contains("\"method\":null");
    }

    // 404 carrying NOT_FOUND, never 403: a refusal that admits the resource exists is the leak the
    // scoped-query rule exists to prevent.
    private static void assertNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    // An Event whose check-in window is already open and whose registration has not closed.
    private String openEvent(Session officer, int capacity) throws Exception {
        Instant startsAt = Instant.now().plus(Duration.ofMinutes(10));
        HttpResponse<String> created = officer.createDraft(clubId, capacity, startsAt);
        String eventId = extractString(created.body(), "id");
        officer.post("/events/" + eventId + "/publication", "");
        return eventId;
    }

    private static String scan(String doorCodeBody) {
        return "{\"token\":\"" + extractString(doorCodeBody, "token") + "\"}";
    }

    // The first roster row whose attendance method is the one asked for — null meaning "not present yet".
    private static String studentIdOf(String rosterBody, String method) {
        for (String row : rosterBody.split("\\{\"studentId\"")) {
            if (!row.startsWith(":\"")) {
                continue;
            }
            boolean matches = method == null
                    ? row.contains("\"method\":null")
                    : row.contains("\"method\":\"" + method + "\"");
            if (matches) {
                return row.substring(2, row.indexOf('"', 2));
            }
        }
        throw new AssertionError("No roster row with method " + method + " in " + rosterBody);
    }

    private static int scannedRows(String rosterBody) {
        return countOf(rosterBody, "\"method\":\"SCANNED\"");
    }

    private static int manualRows(String rosterBody) {
        return countOf(rosterBody, "\"method\":\"MANUAL\"");
    }

    private static int countOf(String body, String needle) {
        return (body.length() - body.replace(needle, "").length()) / needle.length();
    }

    private static String extractString(String body, String field) {
        int start = body.indexOf("\"" + field + "\":\"") + field.length() + 4;
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

        HttpResponse<String> createDraft(String clubId, int capacity, Instant startsAt) throws Exception {
            String body = "{\"title\":\"Intro to Climbing\",\"description\":\"ropes and knots\","
                    + "\"registrationOpensAt\":\"2020-01-01T00:00:00Z\","
                    + "\"registrationClosesAt\":\"2099-03-10T00:00:00Z\","
                    + "\"startsAt\":\"" + startsAt + "\","
                    + "\"endsAt\":\"" + startsAt.plus(Duration.ofHours(1)) + "\","
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
