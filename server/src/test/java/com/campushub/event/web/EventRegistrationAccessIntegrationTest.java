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
    private static final String SHIRT_FIELD_ID = "507f1f77bcf86cd799439011";
    private static final String TOPICS_FIELD_ID = "507f1f77bcf86cd799439012";
    private static final String SHIRT_FORM = "{\"fields\":[{\"type\":\"SINGLE_CHOICE\",\"fieldId\":\""
            + SHIRT_FIELD_ID + "\",\"label\":\"T-shirt\",\"helpText\":null,\"required\":true,"
            + "\"options\":[\"S\",\"M\",\"L\"]}]}";

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
    private String otherClubId;
    private String officerEmail;
    private String otherOfficerEmail;
    private String studentAEmail;
    private String studentBEmail;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        clubId = clubModule.createClub("Robotics Club");

        String suffix = UUID.randomUUID().toString();
        otherClubId = clubModule.createClub("Chess Club " + suffix);
        officerEmail = "officer-" + suffix + "@event-registration-access-test.campushub";
        otherOfficerEmail = "other-officer-" + suffix + "@event-registration-access-test.campushub";
        studentAEmail = "student-a-" + suffix + "@event-registration-access-test.campushub";
        studentBEmail = "student-b-" + suffix + "@event-registration-access-test.campushub";
        adminEmail = "admin-" + suffix + "@event-registration-access-test.campushub";

        String hash = passwordEncoder.encode(PASSWORD);
        Account officer = accountRepository.insert(new Account(officerEmail, hash, "Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(clubId, officer.getId());
        Account otherOfficer = accountRepository.insert(
                new Account(otherOfficerEmail, hash, "Other Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(otherClubId, otherOfficer.getId());
        accountRepository.insert(new Account(studentAEmail, hash, "Student A", SystemRole.STUDENT));
        accountRepository.insert(new Account(studentBEmail, hash, "Student B", SystemRole.STUDENT));
        accountRepository.insert(new Account(adminEmail, hash, "Campus Admin", SystemRole.UNIVERSITY_ADMIN));
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
    void aLosingWriterAgainstAFullEventJoinsTheWaitlist() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEvent(officer, 1);

        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        HttpResponse<String> winner = studentA.post("/events/" + eventId + "/registration", "");
        assertThat(winner.statusCode()).isEqualTo(200);

        Session studentB = Session.signIn(port, studentBEmail, PASSWORD);
        HttpResponse<String> loser = studentB.post("/events/" + eventId + "/registration", "");

        assertThat(loser.statusCode()).isEqualTo(200);
        assertThat(loser.body())
                .contains("\"enrolled\":false")
                .contains("\"waitlistPosition\":1");
    }

    @Test
    void withdrawingASeatPromotesTheWaitlistHeadAndTheStudentCanSeeWhyTheyAreIn() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEvent(officer, 1);
        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        Session studentB = Session.signIn(port, studentBEmail, PASSWORD);
        studentA.post("/events/" + eventId + "/registration", "");
        studentB.post("/events/" + eventId + "/registration", "");

        HttpResponse<String> withdrawn = studentA.delete("/events/" + eventId + "/registration");
        HttpResponse<String> promotedView = studentB.get("/events/" + eventId + "/registration");

        assertThat(withdrawn.statusCode()).isEqualTo(200);
        assertThat(withdrawn.body()).contains("\"enrolled\":false");
        assertThat(promotedView.body())
                .contains("\"enrolled\":true")
                .contains("\"enrollmentVia\":\"PROMOTED\"")
                .contains("\"waitlistPosition\":null");
    }

    @Test
    void leavingTheWaitlistPromotesNobody() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEvent(officer, 1);
        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        Session studentB = Session.signIn(port, studentBEmail, PASSWORD);
        studentA.post("/events/" + eventId + "/registration", "");
        studentB.post("/events/" + eventId + "/registration", "");

        HttpResponse<String> left = studentB.delete("/events/" + eventId + "/registration");
        HttpResponse<String> enrolledView = studentA.get("/events/" + eventId + "/registration");

        assertThat(left.statusCode()).isEqualTo(200);
        assertThat(left.body())
                .contains("\"waitlistPosition\":null")
                .contains("\"waitlistCount\":0");
        assertThat(enrolledView.body())
                .contains("\"enrolled\":true")
                .contains("\"enrollmentVia\":\"DIRECT\"");
    }

    @Test
    void aStudentCannotSeeOrRegisterForAStillUnpublishedDraft() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = extractId(officer.createDraft(clubId, 5).body());

        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        HttpResponse<String> view = studentA.get("/events/" + eventId + "/registration");
        HttpResponse<String> attempt = studentA.post("/events/" + eventId + "/registration", "");

        assertNotFound(view);
        assertNotFound(attempt);
    }

    // The third way a Student could reach a Draft, after the registration view and the registration
    // write above: discovery. EventRepositoryBrowseIntegrationTest already proves the filters and the
    // ordering against real Mongo, so this asserts only what a query below the HTTP layer cannot — that
    // clubId and size reach the query rather than being dropped, that Published-only survives the whole
    // stack, and that the Student's item carries Phase rather than Status. Filtering by clubId is what
    // makes the single expected item exact, so every other assertion here is about that one Event —
    // this class shares one database across its tests. See docs/adr/16-define-event-discovery.md.
    @Test
    void discoveryBindsItsQueryParametersAndNeverCarriesADraft() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String publishedId = publishEvent(officer, 5);
        String draftId = extractId(officer.createDraft(clubId, 5).body());

        Session studentA = Session.signIn(port, studentAEmail, PASSWORD);
        HttpResponse<String> discovered = studentA.get(
                "/events?clubId=" + clubId + "&openForRegistration=true&sort=STARTS_AT_ASC&size=50");
        HttpResponse<String> otherClub = studentA.get("/events?clubId=" + otherClubId);

        assertThat(discovered.statusCode()).isEqualTo(200);
        assertThat(discovered.body())
                .contains(publishedId)
                // One item, so the Phase below is that Event's and not some neighbour's, and `size`
                // echoed back at 50 rather than the controller's default of 20 is the parameter binding.
                .contains("\"total\":1")
                .contains("\"size\":50")
                .contains("\"phase\":\"REGISTRATION_OPEN\"")
                .doesNotContain(draftId);
        // The control for that absence: the Draft exists and its own Officer reads it by id. Without
        // this, a read that returned nothing at all — a mis-bound clubId, say — would pass.
        assertThat(officer.get("/events/" + draftId).body()).contains("\"status\":\"DRAFT\"");
        // And the control for clubId binding in the other direction: the same read scoped to a Club
        // that owns none of these Events comes back empty rather than ignoring the parameter.
        assertThat(otherClub.body()).contains("\"total\":0").doesNotContain(publishedId);
    }

    @Test
    void anOfficerBuildsAFormAStudentAnswersItAndTheSameColumnsReachCsv() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = extractId(officer.createDraft(clubId, 5).body());
        String form = "{\"fields\":["
                + "{\"type\":\"SINGLE_CHOICE\",\"fieldId\":\"" + SHIRT_FIELD_ID
                + "\",\"label\":\"T-shirt\","
                + "\"helpText\":null,\"required\":true,\"options\":[\"S\",\"M\",\"L\"]},"
                + "{\"type\":\"MULTIPLE_CHOICE\",\"fieldId\":\"" + TOPICS_FIELD_ID
                + "\",\"label\":\"Topics\","
                + "\"helpText\":\"Choose any\",\"required\":false,"
                + "\"options\":[\"AI\",\"Robotics\"]}]}";

        HttpResponse<String> built = officer.put("/events/" + eventId + "/registration-form", form);
        officer.post("/events/" + eventId + "/publication", "");
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        HttpResponse<String> invalid = student.post(
                "/events/" + eventId + "/registration", "{\"answers\":{}}");
        HttpResponse<String> afterInvalid = student.get("/events/" + eventId + "/registration");
        HttpResponse<String> registered = student.post(
                "/events/" + eventId + "/registration",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"M\",\"" + TOPICS_FIELD_ID
                        + "\":[\"AI\",\"Robotics\"]}}");
        HttpResponse<String> answers = officer.get("/events/" + eventId + "/registration-answers");
        HttpResponse<String> csv = officer.get("/events/" + eventId + "/registration-answers/csv");
        HttpResponse<String> locked = officer.put(
                "/events/" + eventId + "/registration-form", "{\"fields\":[]}");

        assertThat(built.statusCode()).isEqualTo(200);
        assertThat(built.body())
                .contains("\"fieldId\":\"" + SHIRT_FIELD_ID + "\"")
                .contains("\"type\":\"SINGLE_CHOICE\"");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body())
                .contains("\"code\":\"FORM_VALIDATION_FAILED\"")
                .contains("\"" + SHIRT_FIELD_ID + "\":\"Required.\"");
        assertThat(afterInvalid.body()).contains("\"enrolledCount\":0");
        assertThat(registered.statusCode()).isEqualTo(200);
        assertThat(registered.body()).contains("\"enrolled\":true").contains("\"answersSaved\":true");
        assertThat(answers.body())
                .contains("\"answersSaved\":true")
                .contains("\"fieldId\":\"" + SHIRT_FIELD_ID + "\",\"option\":\"M\",\"count\":1");
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body())
                .startsWith("Student,Route in,Answers status,T-shirt,Topics")
                .contains("M,\"AI; Robotics\"");
        assertThat(locked.statusCode()).isEqualTo(409);
        assertThat(locked.body()).contains("\"code\":\"FORM_LOCKED\"");
    }

    // The retry route exists for the one outcome the Seat Ledger write cannot make atomic with the
    // answer write: the Seat is held but the answers were lost. The Student's browser offers it at
    // web/src/features/registration/components/EventRegistrationPage.tsx when answersSaved is false.
    // A real HTTP test cannot induce that half-failure, and does not need to — the route's contract is
    // "replace my saved answers, leave my Seat alone", which registering and then retrying exercises
    // end to end. Otherwise it is covered only against mocks, by
    // RegistrationModuleImplTest.retryWritesOnlyTheMissingAnswersWithoutTouchingTheSeat.
    @Test
    void aStudentRetriesTheirAnswersAndTheCorrectionReachesTheOfficerReport() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEventWithShirtForm(officer, 5);
        Session student = Session.signIn(port, studentAEmail, PASSWORD);
        student.post(
                "/events/" + eventId + "/registration",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"M\"}}");
        HttpResponse<String> beforeRetry = officer.get("/events/" + eventId + "/registration-answers");

        HttpResponse<String> retried = student.put(
                "/events/" + eventId + "/registration/answers",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"L\"}}");
        HttpResponse<String> reread = student.get("/events/" + eventId + "/registration");
        HttpResponse<String> afterRetry = officer.get("/events/" + eventId + "/registration-answers");

        assertThat(retried.statusCode()).isEqualTo(200);
        assertThat(retried.body())
                .contains("\"answersSaved\":true")
                .contains("\"" + SHIRT_FIELD_ID + "\":\"L\"");
        // Read back in a separate request, because the response above is built from the map the Student
        // just submitted and would look identical if nothing had reached Mongo. The same read proves the
        // Seat was untouched: retrying answers is not a re-registration.
        assertThat(reread.body())
                .contains("\"" + SHIRT_FIELD_ID + "\":\"L\"")
                .contains("\"enrolled\":true")
                .contains("\"enrollmentVia\":\"DIRECT\"")
                .contains("\"enrolledCount\":1");
        // The retry replaces the Student's one Registration rather than adding a second: the earlier "M"
        // has to be gone afterwards, and the before-shot is the control that it was ever counted.
        assertThat(beforeRetry.body())
                .contains("\"fieldId\":\"" + SHIRT_FIELD_ID + "\",\"option\":\"M\",\"count\":1");
        assertThat(afterRetry.body())
                .contains("\"fieldId\":\"" + SHIRT_FIELD_ID + "\",\"option\":\"L\",\"count\":1")
                .doesNotContain("\"option\":\"M\",\"count\":1");
    }

    // Both ways the retry route refuses, each against a control that shows the refusal is specific:
    // invalid answers leave the previously saved ones standing, and holding a Waitlist place is not
    // holding a Seat even though the Event itself is perfectly visible to that Student.
    @Test
    void aRetryIsRefusedForInvalidAnswersAndForAWaitlistedStudentWhoHoldsNoSeat() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEventWithShirtForm(officer, 1);
        Session seatHolder = Session.signIn(port, studentAEmail, PASSWORD);
        Session waitlisted = Session.signIn(port, studentBEmail, PASSWORD);
        seatHolder.post(
                "/events/" + eventId + "/registration",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"M\"}}");
        waitlisted.post(
                "/events/" + eventId + "/registration",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"S\"}}");

        HttpResponse<String> invalid = seatHolder.put(
                "/events/" + eventId + "/registration/answers", "{\"answers\":{}}");
        HttpResponse<String> afterInvalid = seatHolder.get("/events/" + eventId + "/registration");
        HttpResponse<String> withoutASeat = waitlisted.put(
                "/events/" + eventId + "/registration/answers",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"S\"}}");
        HttpResponse<String> waitlistedView = waitlisted.get("/events/" + eventId + "/registration");

        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body())
                .contains("\"code\":\"FORM_VALIDATION_FAILED\"")
                .contains("\"" + SHIRT_FIELD_ID + "\":\"Required.\"");
        // A rejected retry is not a destructive one — the answers written at registration survive it.
        assertThat(afterInvalid.body())
                .contains("\"answersSaved\":true")
                .contains("\"" + SHIRT_FIELD_ID + "\":\"M\"");
        assertNotFound(withoutASeat);
        // The control for that 404: this Student can read the Event's registration state perfectly well,
        // so the refusal is about the missing Seat and not about the Event being hidden from them.
        assertThat(waitlistedView.statusCode()).isEqualTo(200);
        assertThat(waitlistedView.body())
                .contains("\"enrolled\":false")
                .contains("\"waitlistPosition\":1");
    }

    @Test
    void anOfficerCannotBuildOrReadRegistrationFormsForAnotherClub() throws Exception {
        Session otherOfficer = Session.signIn(port, otherOfficerEmail, PASSWORD);
        String otherEventId = extractId(otherOfficer.createDraft(otherClubId, 5).body());
        Session officer = Session.signIn(port, officerEmail, PASSWORD);

        HttpResponse<String> formEdit = officer.put(
                "/events/" + otherEventId + "/registration-form", "{\"fields\":[]}");
        HttpResponse<String> answers = officer.get(
                "/events/" + otherEventId + "/registration-answers");
        HttpResponse<String> csv = officer.get(
                "/events/" + otherEventId + "/registration-answers/csv");

        assertNotFound(formEdit);
        assertNotFound(answers);
        assertNotFound(csv);
    }

    // The privacy boundary from the other two directions, which the cross-club test cannot show: form
    // answers belong to the owning Club's Officers, so a Student — including the one who wrote the
    // answers — and a University Admin, whose legitimate interest is aggregate, are both refused. See
    // docs/adr/08-define-roles-and-resource-authorization.md.
    @Test
    void neitherAStudentNorAUniversityAdminMayReadTheFormAnswers() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String eventId = publishEventWithShirtForm(officer, 5);
        Session author = Session.signIn(port, studentAEmail, PASSWORD);
        author.post(
                "/events/" + eventId + "/registration",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"M\"}}");

        Session otherStudent = Session.signIn(port, studentBEmail, PASSWORD);
        Session admin = Session.signIn(port, adminEmail, PASSWORD);

        assertNotFound(author.get("/events/" + eventId + "/registration-answers"));
        assertNotFound(otherStudent.get("/events/" + eventId + "/registration-answers"));
        assertNotFound(otherStudent.get("/events/" + eventId + "/registration-answers/csv"));
        assertNotFound(admin.get("/events/" + eventId + "/registration-answers"));
        assertNotFound(admin.get("/events/" + eventId + "/registration-answers/csv"));
        // The owning Officer still reads them: the refusals above are a boundary, not a broken route.
        assertThat(officer.get("/events/" + eventId + "/registration-answers").statusCode())
                .isEqualTo(200);
    }

    // The form has to be built while the Event is still a Draft — publishing locks it — so the three
    // tests that need answers to exist all open with the same three calls in the same order.
    private String publishEventWithShirtForm(Session officer, int capacity) throws Exception {
        String eventId = extractId(officer.createDraft(clubId, capacity).body());
        officer.put("/events/" + eventId + "/registration-form", SHIRT_FORM);
        officer.post("/events/" + eventId + "/publication", "");
        return eventId;
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

    private static void assertNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
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

        HttpResponse<String> put(String path, String jsonBody) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                    .PUT(BodyPublishers.ofString(jsonBody))
                    .header("Content-Type", "application/json");
            csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> delete(String path) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path))).DELETE();
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
