package com.campushub;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * The session review, asked of the routing table rather than of a list somebody keeps up to date.
 *
 * <p>Both sweeps enumerate every route Spring has actually mapped, so a controller added next month
 * is covered by them the day it is written — which is the only version of "every unsafe route carries
 * CSRF" and "no form answer reaches a DTO" that stays true after the pull request that asserts it.
 *
 * <p>The refusals are asserted with a control alongside each one: a request that should be refused
 * and the same request that should not, because "everything returned 403" is also what a misspelled
 * URL returns.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SessionSecurityIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String SHIRT_FIELD_ID = "507f1f77bcf86cd799439011";
    // Distinctive enough that finding it anywhere is finding this Student's answer and not a coincidence.
    private static final String ANSWER = "S";
    private static final String FREE_TEXT_FIELD_ID = "507f1f77bcf86cd799439012";
    private static final String FREE_TEXT_ANSWER = "wheelchair-access-please-9f2c1a44";
    private static final String UNUSED_ID = "000000000000000000000000";
    private static final Set<RequestMethod> UNSAFE =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/session-security-test");
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
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private String clubId;
    private String officerEmail;
    private String studentEmail;
    private String adminEmail;
    private String studentId;
    private String eventId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        clubId = clubModule.createClub("Robotics Society " + suffix);
        officerEmail = "officer-" + suffix + "@session-security-test.campushub";
        studentEmail = "student-" + suffix + "@session-security-test.campushub";
        adminEmail = "admin-" + suffix + "@session-security-test.campushub";

        String hash = passwordEncoder.encode(PASSWORD);
        Account officer = accountRepository.insert(new Account(officerEmail, hash, "Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(clubId, officer.getId());
        studentId = accountRepository
                .insert(new Account(studentEmail, hash, "Ada Lovelace", SystemRole.STUDENT))
                .getId();
        accountRepository.insert(new Account(adminEmail, hash, "Registrar", SystemRole.UNIVERSITY_ADMIN));

        eventId = publishAnEventAndAnswerItsForm();
    }

    // ---- CSRF ----

    @Test
    void everyUnsafeRouteRefusesARequestThatDoesNotCarryTheCsrfToken() throws Exception {
        List<Route> routes = unsafeRoutes();
        assertThat(routes).as("the routing table should have unsafe routes in it").isNotEmpty();

        List<String> reached = new ArrayList<>();
        for (Route route : routes) {
            Session session = Session.signIn(port, officerEmail, PASSWORD);
            HttpResponse<String> withoutToken = session.sendWithoutCsrfToken(route.method(), route.path());
            if (withoutToken.statusCode() != 403) {
                reached.add(route + " answered " + withoutToken.statusCode());
            }
        }

        assertThat(reached).as("unsafe routes that a request without a CSRF token got through to").isEmpty();
    }

    // Without this, the sweep above passes just as happily against a server that answers 403 to
    // everything, including routes that do not exist.
    @Test
    void theSameUnsafeRoutesAreReachedOnceTheCsrfTokenIsCarried() throws Exception {
        List<String> stillRefused = new ArrayList<>();
        for (Route route : unsafeRoutes()) {
            Session session = Session.signIn(port, officerEmail, PASSWORD);
            HttpResponse<String> withToken = session.send(route.method(), route.path(), "{}");
            if (withToken.statusCode() == 403) {
                stillRefused.add(route.toString());
            }
        }

        assertThat(stillRefused)
                .as("a route that stays 403 with a valid token is refusing for some other reason")
                .isEmpty();
    }

    // ---- Cookie attributes ----

    @Test
    void theSessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
        Session session = Session.signIn(port, studentEmail, PASSWORD);

        String sessionCookie = session.setCookieHeaderFor("SESSION");

        assertThat(sessionCookie).containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");
    }

    @Test
    void theCsrfCookieIsReadableByScriptBecauseTheDoubleSubmitDependsOnItAndIsStillSameSiteLax()
            throws Exception {
        Session session = Session.signIn(port, studentEmail, PASSWORD);

        String csrfCookie = session.setCookieHeaderFor("XSRF-TOKEN");

        assertThat(csrfCookie).doesNotContainIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");
    }

    // Secure is not asserted as present: it mirrors the request's scheme, and this suite speaks plain
    // HTTP. What is worth pinning is that nothing has hardcoded it on, which would make the cookie
    // silently undeliverable everywhere TLS is not terminated — compose, CI and a laptop included.
    @Test
    void neitherCookieIsMarkedSecureOverPlainHttpSoLocalAndComposeEnvironmentsStillWork() throws Exception {
        Session session = Session.signIn(port, studentEmail, PASSWORD);

        assertThat(session.setCookieHeaderFor("SESSION")).doesNotContainIgnoringCase("Secure");
        assertThat(session.setCookieHeaderFor("XSRF-TOKEN")).doesNotContainIgnoringCase("Secure");
    }

    // ---- No form answer reaches a DTO a University Admin can read ----

    @Test
    void aStudentsFormAnswersAreAbsentFromEveryDtoAUniversityAdminCanReach() throws Exception {
        Session admin = Session.signIn(port, adminEmail, PASSWORD);

        List<String> leaked = new ArrayList<>();
        for (Route route : readableRoutes()) {
            String body = admin.send(route.method(), route.path(), "").body();
            if (body.contains(FREE_TEXT_ANSWER) || body.contains("\"answers\"")) {
                leaked.add(route + " returned an answer");
            }
        }

        assertThat(leaked)
                .as("form answers belong to the owning Club's Officers and to nobody else")
                .isEmpty();
    }

    // The sweep above would also pass if the answer had never been recorded. This is the control: the
    // owning Club's Officer reads exactly the answer the sweep is looking for.
    @Test
    void theOwningClubsOfficerDoesReadTheAnswerTheAdminSweepIsLookingFor() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);

        HttpResponse<String> csv = officer.get("/events/" + eventId + "/registration-answers/csv");

        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body()).contains(FREE_TEXT_ANSWER);
    }

    // ---- The routing table ----

    private List<Route> unsafeRoutes() {
        return routes(UNSAFE::contains);
    }

    private List<Route> readableRoutes() {
        return routes(RequestMethod.GET::equals);
    }

    private List<Route> routes(Predicate<RequestMethod> wanted) {
        Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::toString));
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                if (!wanted.test(method)) {
                    continue;
                }
                for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                    if (pattern.startsWith("/api")) {
                        routes.add(new Route(method, substitute(pattern)));
                    }
                }
            }
        }
        return List.copyOf(routes);
    }

    /**
     * Path variables filled with the ids of the fixture this test built, so a swept route returns the
     * real payload rather than a 404 that would hide whatever it holds. Anything unrecognised gets an
     * id that belongs to nothing, which is the right answer for a route this fixture cannot populate.
     */
    private String substitute(String pattern) {
        Map<String, String> values =
                Map.of("{eventId}", eventId, "{clubId}", clubId, "{studentId}", studentId);
        String path = pattern;
        for (Map.Entry<String, String> value : values.entrySet()) {
            path = path.replace(value.getKey(), value.getValue());
        }
        return path.replaceAll("\\{[^}]+}", UNUSED_ID);
    }

    private record Route(RequestMethod method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private String publishAnEventAndAnswerItsForm() throws Exception {
        Session officer = Session.signIn(port, officerEmail, PASSWORD);
        String created = officer.post(
                        "/clubs/" + clubId + "/events",
                        "{\"title\":\"Robotics Night\",\"description\":\"build things\","
                                + "\"registrationOpensAt\":\"2020-01-01T00:00:00Z\","
                                + "\"registrationClosesAt\":\"2099-03-10T00:00:00Z\","
                                + "\"startsAt\":\"2099-03-20T00:00:00Z\","
                                + "\"endsAt\":\"2099-03-20T02:00:00Z\",\"capacity\":5}")
                .body();
        String id = extractString(created, "id");
        officer.put(
                "/events/" + id + "/registration-form",
                "{\"fields\":["
                        + "{\"type\":\"SINGLE_CHOICE\",\"fieldId\":\"" + SHIRT_FIELD_ID
                        + "\",\"label\":\"T-shirt\",\"helpText\":null,\"required\":true,"
                        + "\"options\":[\"S\",\"M\",\"L\"]},"
                        + "{\"type\":\"SHORT_TEXT\",\"fieldId\":\"" + FREE_TEXT_FIELD_ID
                        + "\",\"label\":\"Access needs\",\"helpText\":null,\"required\":true,"
                        + "\"maxLength\":200}]}");
        officer.post("/events/" + id + "/publication", "");

        Session student = Session.signIn(port, studentEmail, PASSWORD);
        student.post(
                "/events/" + id + "/registration",
                "{\"answers\":{\"" + SHIRT_FIELD_ID + "\":\"" + ANSWER + "\",\"" + FREE_TEXT_FIELD_ID
                        + "\":\"" + FREE_TEXT_ANSWER + "\"}}");
        return id;
    }

    private static String extractString(String body, String field) {
        int start = body.indexOf("\"" + field + "\":\"") + field.length() + 4;
        return body.substring(start, body.indexOf('"', start));
    }

    private static final class Session {

        private final int port;
        private final CookieManager cookieManager = new CookieManager();
        private final HttpClient client;
        private final List<String> setCookieHeaders = new ArrayList<>();

        private Session(int port) {
            this.port = port;
            this.client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        }

        static Session signIn(int port, String email, String password) throws Exception {
            Session session = new Session(port);
            session.get("/auth/me");
            session.signInAsExistingSession(email, password);
            return session;
        }

        void signInAsExistingSession(String email, String password) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url("/auth/login")))
                    .POST(BodyPublishers.ofString("email=" + email + "&password=" + password))
                    .header("Content-Type", "application/x-www-form-urlencoded");
            csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
            record(client.send(builder.build(), HttpResponse.BodyHandlers.discarding()));
            get("/auth/me");
        }

        HttpResponse<String> get(String path) throws Exception {
            return send(RequestMethod.GET, path, "");
        }

        HttpResponse<String> post(String path, String jsonBody) throws Exception {
            return send(RequestMethod.POST, path, jsonBody);
        }

        HttpResponse<String> put(String path, String jsonBody) throws Exception {
            return send(RequestMethod.PUT, path, jsonBody);
        }

        HttpResponse<String> send(RequestMethod method, String path, String jsonBody) throws Exception {
            HttpRequest.Builder builder = requestFor(method, path, jsonBody);
            csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
            return record(client.send(builder.build(), HttpResponse.BodyHandlers.ofString()));
        }

        HttpResponse<String> sendWithoutCsrfToken(RequestMethod method, String path) throws Exception {
            return record(client.send(
                    requestFor(method, path, "{}").build(), HttpResponse.BodyHandlers.ofString()));
        }

        private HttpRequest.Builder requestFor(RequestMethod method, String path, String jsonBody) {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "application/json");
            return switch (method) {
                case GET -> builder.GET();
                case DELETE -> builder.DELETE();
                default -> builder.method(method.name(), BodyPublishers.ofString(jsonBody));
            };
        }

        private <T> HttpResponse<T> record(HttpResponse<T> response) {
            setCookieHeaders.addAll(response.headers().allValues("Set-Cookie"));
            return response;
        }

        String setCookieHeaderFor(String name) {
            return setCookieHeaders.stream()
                    .filter(header -> header.startsWith(name + "="))
                    .reduce((first, last) -> last)
                    .orElseThrow(() -> new AssertionError("no " + name + " cookie was ever set"));
        }

        Optional<String> cookieValue(String name) {
            return cookieManager.getCookieStore().getCookies().stream()
                    .filter(cookie -> name.equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst();
        }

        private Optional<String> csrfToken() {
            return cookieValue("XSRF-TOKEN");
        }

        private String url(String path) {
            return "http://localhost:" + port + "/api" + path;
        }
    }
}
