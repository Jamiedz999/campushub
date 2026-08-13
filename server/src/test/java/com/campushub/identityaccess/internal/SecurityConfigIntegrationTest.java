package com.campushub.identityaccess.internal;

import static org.assertj.core.api.Assertions.assertThat;

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

// Exercises the real Security filter chain end to end over HTTP: login, logout, CSRF, and the
// "unauthenticated access to any internal route is refused" acceptance criterion from Issue #2. Seeds
// its own Account directly (Mongock seeding is a separate concern) so this stays a focused proof of the
// filter chain wiring rather than the seeding pipeline.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityConfigIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/security-config-test");
        registry.add("campushub.security.session-secret", () -> "test-session-secret");
        registry.add("campushub.checkin.hmac-secret", () -> "test-checkin-hmac-secret");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private CookieManager cookieManager;
    private HttpClient client;

    @BeforeEach
    void setUp() {
        cookieManager = new CookieManager();
        client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        if (accountRepository.findByEmail("student@security-config-test.campushub").isEmpty()) {
            accountRepository.insert(new Account(
                    "student@security-config-test.campushub",
                    passwordEncoder.encode(PASSWORD),
                    "Test Student",
                    SystemRole.STUDENT));
        }
    }

    @Test
    void unauthenticatedAccessToAProtectedRouteIsRefused() throws Exception {
        HttpResponse<String> response = get("/api/auth/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void loginWithTheCorrectPasswordSucceedsWithoutARedirect() throws Exception {
        primeCsrfCookie();

        HttpResponse<String> response = login("student@security-config-test.campushub", PASSWORD);

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void loginWithTheWrongPasswordFailsWithInvalidCredentials() throws Exception {
        primeCsrfCookie();

        HttpResponse<String> response = login("student@security-config-test.campushub", "wrong-password");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"INVALID_CREDENTIALS\"");
    }

    @Test
    void loginWithoutTheCsrfHeaderIsRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/auth/login")))
                .POST(BodyPublishers.ofString(
                        "email=student@security-config-test.campushub&password=" + PASSWORD))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void aSignedInSessionCanReachAProtectedRouteAndThenLogOut() throws Exception {
        primeCsrfCookie();
        login("student@security-config-test.campushub", PASSWORD);

        HttpResponse<String> me = get("/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);

        HttpResponse<String> logout = post("/api/auth/logout", "");
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> afterLogout = get("/api/auth/me");
        assertThat(afterLogout.statusCode()).isEqualTo(401);
    }

    private void primeCsrfCookie() throws Exception {
        get("/api/auth/me");
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return post("/api/auth/login", "email=" + email + "&password=" + password);
    }

    private HttpResponse<String> post(String path, String formBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .POST(BodyPublishers.ofString(formBody))
                .header("Content-Type", "application/x-www-form-urlencoded");
        csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path))).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Optional<String> csrfToken() {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
