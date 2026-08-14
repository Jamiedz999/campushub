package com.campushub.identityaccess.web;

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

// The end-to-end proof of Issue #2's acceptance criteria that no single lower-level test covers on its
// own: real HTTP, two independent signed-in sessions (an Admin and an Officer/Student), and the whole
// grant → view → revoke → refused lifecycle happening on the officer's ONE session with no re-login —
// which is what "takes effect on the next request, no token expiry to wait for" actually means.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ClubOfficerAccessIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/club-officer-access-test");
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
    private String adminEmail;
    private String officerEmail;
    private String officerAccountId;
    private String studentEmail;
    private String studentAccountId;

    @BeforeEach
    void setUp() {
        clubAId = clubModule.createClub("Club A");
        clubBId = clubModule.createClub("Club B");

        String suffix = java.util.UUID.randomUUID().toString();
        adminEmail = "admin-" + suffix + "@club-officer-access-test.campushub";
        officerEmail = "officer-" + suffix + "@club-officer-access-test.campushub";
        studentEmail = "student-" + suffix + "@club-officer-access-test.campushub";

        String hash = passwordEncoder.encode(PASSWORD);
        accountRepository.insert(new Account(adminEmail, hash, "Test Admin", SystemRole.UNIVERSITY_ADMIN));
        Account officer = accountRepository.insert(new Account(officerEmail, hash, "Test Officer", SystemRole.STUDENT));
        officerAccountId = officer.getId();
        Account student = accountRepository.insert(new Account(studentEmail, hash, "Test Student", SystemRole.STUDENT));
        studentAccountId = student.getId();
    }

    @Test
    void grantingThenRevokingTakesEffectOnTheOfficersVeryNextRequest() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        Session officer = Session.signIn(port, officerEmail, PASSWORD);

        HttpResponse<String> grant = admin.grantOfficer(clubAId, officerAccountId);
        assertThat(grant.statusCode()).isEqualTo(204);

        HttpResponse<String> beforeRevoke = officer.get("/clubs/" + clubAId + "/officers");
        assertThat(beforeRevoke.statusCode()).isEqualTo(200);
        assertThat(beforeRevoke.body()).contains(officerAccountId);

        HttpResponse<String> revoke = admin.delete("/clubs/" + clubAId + "/officers/" + officerAccountId);
        assertThat(revoke.statusCode()).isEqualTo(204);

        // Same officer session, no re-login: the revoked grant is refused on the very next request.
        HttpResponse<String> afterRevoke = officer.get("/clubs/" + clubAId + "/officers");
        assertThat(afterRevoke.statusCode()).isEqualTo(404);
        assertThat(afterRevoke.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void aStudentHoldingGrantsInTwoClubsIsRepresentableAndCorrect() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        admin.grantOfficer(clubAId, officerAccountId);
        admin.grantOfficer(clubBId, officerAccountId);

        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        HttpResponse<String> me = officer.get("/auth/me");

        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(clubAId);
        assertThat(me.body()).contains(clubBId);

        HttpResponse<String> officersOfA = officer.get("/clubs/" + clubAId + "/officers");
        HttpResponse<String> officersOfB = officer.get("/clubs/" + clubBId + "/officers");
        assertThat(officersOfA.statusCode()).isEqualTo(200);
        assertThat(officersOfB.statusCode()).isEqualTo(200);
    }

    @Test
    void aStudentWithNoGrantsCannotReachTheOfficerOnlyRoute() throws Exception {
        Session student = Session.signIn(port, studentEmail, PASSWORD);

        HttpResponse<String> response = student.get("/clubs/" + clubAId + "/officers");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void anOfficerOfOneClubCannotSeeAnotherClubsOfficers() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        admin.grantOfficer(clubAId, officerAccountId);

        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        HttpResponse<String> response = officer.get("/clubs/" + clubBId + "/officers");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void aStudentCannotGrantOfficerRights() throws Exception {
        Session student = Session.signIn(port, studentEmail, PASSWORD);

        HttpResponse<String> response = student.grantOfficer(clubAId, officerAccountId);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    // Granting is a University Admin responsibility, and holding the grant is not a way to hand it on:
    // an Officer of Club A can neither grant nor revoke in their own Club nor in anyone else's. Without
    // this, "granting officer rights" is an Admin-only rule shown only by an Admin succeeding at it.
    @Test
    void anOfficerCannotGrantOrRevokeOfficerRightsEvenInTheirOwnClub() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);
        admin.grantOfficer(clubAId, officerAccountId);

        Session officer = Session.signIn(port, officerEmail, PASSWORD);

        assertNotFound(officer.grantOfficer(clubAId, studentAccountId));
        assertNotFound(officer.grantOfficer(clubBId, studentAccountId));
        assertNotFound(officer.delete("/clubs/" + clubAId + "/officers/" + officerAccountId));
        // The grant the Admin made is untouched, so none of the three refusals wrote anything.
        assertThat(officer.get("/clubs/" + clubAId + "/officers").body()).contains(officerAccountId);
    }

    // 404 carrying NOT_FOUND, never 403: a refusal that admits the resource exists is the leak the
    // scoped-query rule exists to prevent.
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
            session.get("/auth/me"); // primes the XSRF-TOKEN cookie
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(session.url("/auth/login")))
                    .POST(BodyPublishers.ofString("email=" + email + "&password=" + password))
                    .header("Content-Type", "application/x-www-form-urlencoded");
            session.csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
            session.client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            // Spring Security's CsrfAuthenticationStrategy rotates the token on a successful login, so
            // the pre-login cookie is no longer valid for the next unsafe request. The real frontend
            // never hits this gap: useLogin's onSuccess invalidates useCurrentActor, whose refetch is a
            // GET that lands before SignInPage ever navigates anywhere — re-priming here mirrors that.
            session.get("/auth/me");
            return session;
        }

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url(path))).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> grantOfficer(String clubId, String accountId) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url("/clubs/" + clubId + "/officers")))
                    .POST(BodyPublishers.ofString("{\"accountId\":\"" + accountId + "\"}"))
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
