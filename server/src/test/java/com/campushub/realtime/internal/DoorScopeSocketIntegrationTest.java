package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campushub.club.ClubModule;
import com.campushub.event.EventModule;
import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.identityaccess.persistence.AccountRepository;
import com.campushub.realtime.RealtimeModule;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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

// The socket over a real server, with real sessions, which is the only place the three claims in
// Issue #9's acceptance can all be checked at once: the scope is authorized at the handshake, the
// frames carry no state, and a connection that dropped is not owed a backlog when it comes back.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DoorScopeSocketIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final Instant STARTS = Instant.parse("2099-04-01T18:00:00Z");
    private static final Instant ENDS = Instant.parse("2099-04-01T20:00:00Z");

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO_DB.getConnectionString() + "/door-scope-socket-test");
        registry.add("campushub.security.session-secret", () -> "door-scope-session-secret");
        registry.add("campushub.checkin.hmac-secret", () -> "door-scope-hmac-secret");
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
    private EventModule eventModule;

    @Autowired
    private RealtimeModule realtimeModule;

    // The registry the handler writes to, watched directly so these tests wait for the server to have
    // registered a socket rather than for a duration someone guessed. A publish landing a millisecond
    // before the subscription is a real race for a real door screen too — re-reading on connect is
    // exactly what covers it — but it is not what any of these tests are about.
    @Autowired
    private DoorScopeSessions doorScopeSessions;

    private String eventId;
    private String owningOfficerEmail;
    private String otherClubOfficerEmail;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owningOfficerEmail = "owner-" + suffix + "@door-scope-socket-test.campushub";
        otherClubOfficerEmail = "other-" + suffix + "@door-scope-socket-test.campushub";

        String owningClubId = clubModule.createClub("Owning Club " + suffix);
        String otherClubId = clubModule.createClub("Other Club " + suffix);
        String hash = passwordEncoder.encode(PASSWORD);
        Account owner = accountRepository.insert(
                new Account(owningOfficerEmail, hash, "Door Officer", SystemRole.STUDENT));
        Account other = accountRepository.insert(
                new Account(otherClubOfficerEmail, hash, "Other Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(owningClubId, owner.getId());
        clubModule.grantOfficer(otherClubId, other.getId());

        eventId = eventModule.createDraft(
                owningClubId,
                "Doors Open",
                "An Event with a door",
                STARTS.minusSeconds(86_400),
                STARTS.minusSeconds(3_600),
                STARTS,
                ENDS,
                40);
    }

    @Test
    void theOwningClubsOfficerReceivesHintsThatCarryNoState() throws Exception {
        DoorScreen screen = DoorScreen.open(port, owningOfficerEmail, eventId);
        awaitSubscribers(1);

        realtimeModule.publishAttendanceChanged(eventId);

        assertThat(screen.nextFrame())
                .isEqualTo("{\"type\":\"attendance-changed\",\"eventId\":\"" + eventId + "\"}");
        screen.close();
    }

    @Test
    void anOfficerOfAnotherClubCannotSubscribe() {
        // The acceptance criterion, over a real handshake: refused with 404, exactly as the HTTP door
        // code for the same Event is refused, and with no socket left open to receive anything.
        assertThatThrownBy(() -> DoorScreen.open(port, otherClubOfficerEmail, eventId))
                .hasCauseInstanceOf(WebSocketHandshakeException.class)
                .cause()
                .satisfies(handshake ->
                        assertThat(((WebSocketHandshakeException) handshake).getResponse().statusCode())
                                .isEqualTo(404));
    }

    @Test
    void aSocketWithNoSessionAtAllIsRefusedBeforeTheScopeIsEvenConsidered() {
        assertThatThrownBy(() -> DoorScreen.openAnonymously(port, eventId))
                .hasCauseInstanceOf(WebSocketHandshakeException.class)
                .cause()
                .satisfies(handshake ->
                        assertThat(((WebSocketHandshakeException) handshake).getResponse().statusCode())
                                .isEqualTo(401));
    }

    @Test
    void aReconnectedScreenIsOwedNoBacklogOfWhatItMissed() throws Exception {
        DoorScreen screen = DoorScreen.open(port, owningOfficerEmail, eventId);
        awaitSubscribers(1);
        screen.close();
        awaitSubscribers(0);

        // Three check-ins happen while the projector's wifi is out.
        realtimeModule.publishAttendanceChanged(eventId);
        realtimeModule.publishAttendanceChanged(eventId);
        realtimeModule.publishAttendanceChanged(eventId);

        DoorScreen reconnected = DoorScreen.open(port, owningOfficerEmail, eventId);
        awaitSubscribers(1);

        // Nothing is replayed, on purpose. A queue of missed hints would be a queue that can be wrong;
        // the screen converges instead by re-reading its snapshot the moment it reconnects, which is
        // the same path it takes for a single lost frame. The client half of this is proven in
        // web/src/features/checkin/hooks/useAttendanceRoster.test.tsx.
        assertThat(reconnected.frameWithin(1, TimeUnit.SECONDS)).isEmpty();

        realtimeModule.publishAttendanceChanged(eventId);
        assertThat(reconnected.nextFrame()).isNotEmpty();
        reconnected.close();
    }

    private void awaitSubscribers(int expected) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (doorScopeSessions.inScope(eventId).size() != expected && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
        assertThat(doorScopeSessions.inScope(eventId)).hasSize(expected);
    }

    /** A door screen: an authenticated browser session, and the socket it opened with those cookies. */
    private static final class DoorScreen implements WebSocket.Listener {

        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private WebSocket socket;

        static DoorScreen open(int port, String email, String eventId) throws Exception {
            return connect(port, eventId, Optional.of(signIn(port, email)));
        }

        static DoorScreen openAnonymously(int port, String eventId) throws Exception {
            return connect(port, eventId, Optional.empty());
        }

        private static DoorScreen connect(int port, String eventId, Optional<String> cookies) throws Exception {
            DoorScreen screen = new DoorScreen();
            WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
            cookies.ifPresent(header -> builder.header("Cookie", header));
            screen.socket = builder.buildAsync(
                            URI.create("ws://localhost:" + port + "/ws/events/" + eventId + "/attendance"),
                            screen)
                    .get(10, TimeUnit.SECONDS);
            return screen;
        }

        // Form login over HTTP, then the session cookies it left behind — the same two steps a browser
        // takes before its door screen ever opens a socket.
        private static String signIn(int port, String email) throws Exception {
            CookieManager cookies = new CookieManager();
            HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
            String base = "http://localhost:" + port + "/api";
            client.send(
                    HttpRequest.newBuilder(URI.create(base + "/auth/me")).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            HttpRequest.Builder login = HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                    .POST(BodyPublishers.ofString("email=" + email + "&password=" + PASSWORD))
                    .header("Content-Type", "application/x-www-form-urlencoded");
            csrfToken(cookies).ifPresent(token -> login.header("X-XSRF-TOKEN", token));
            client.send(login.build(), HttpResponse.BodyHandlers.discarding());
            return cookies.getCookieStore().getCookies().stream()
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; "));
        }

        private static Optional<String> csrfToken(CookieManager cookies) {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst();
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            frames.add(data.toString());
            webSocket.request(1);
            return null;
        }

        String nextFrame() throws InterruptedException {
            return frameWithin(10, TimeUnit.SECONDS);
        }

        String frameWithin(long timeout, TimeUnit unit) throws InterruptedException {
            return Optional.ofNullable(frames.poll(timeout, unit)).orElse("");
        }

        void close() throws Exception {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);
        }
    }
}
