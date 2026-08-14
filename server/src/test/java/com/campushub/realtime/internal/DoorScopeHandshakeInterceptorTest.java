package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.realtime.RealtimeModule.DoorScopeAuthorizer;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

// The negative case here is the acceptance criterion "an Officer of another Club cannot subscribe".
// It is checked at the handshake because that is the last moment the connection is still an HTTP
// request with a session behind it — after this, the socket outlives every filter that could refuse it.
class DoorScopeHandshakeInterceptorTest {

    private static final CurrentActor OFFICER_OF_CLUB_A = new CurrentActor(
            "account-1", "officer@campus.example", "An Officer", SystemRole.STUDENT, Set.of("club-a"));

    private final IdentityAccessModule identityAccessModule = mock(IdentityAccessModule.class);
    private final DoorScopeAuthorizer authorizer = mock(DoorScopeAuthorizer.class);
    private final DoorScopeHandshakeInterceptor interceptor =
            new DoorScopeHandshakeInterceptor(identityAccessModule, authorizer);

    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final Map<String, Object> attributes = new HashMap<>();

    @BeforeEach
    void signIn() {
        when(identityAccessModule.currentActor()).thenReturn(OFFICER_OF_CLUB_A);
    }

    @Test
    void theOwningClubsOfficerIsLetInWithTheScopeRecordedOnTheSession() {
        when(authorizer.mayWatchDoor("event-1", Set.of("club-a"))).thenReturn(true);

        boolean allowed = interceptor.beforeHandshake(
                requestTo("/ws/events/event-1/attendance"), response, null, attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes).containsEntry(DoorSocketHandler.SCOPE_ATTRIBUTE, "event-1");
        verifyNoInteractions(response);
    }

    @Test
    void anOfficerOfAnotherClubIsRefusedAndToldTheDoorDoesNotExist() {
        // 404 rather than 403, like every other authorization failure here: the query was scoped by
        // this caller's grants, so another Club's door genuinely is not found. The session is left with
        // no scope attribute, so even a refused handshake that somehow proceeded would subscribe to
        // nothing.
        when(authorizer.mayWatchDoor("event-1", Set.of("club-a"))).thenReturn(false);

        boolean allowed = interceptor.beforeHandshake(
                requestTo("/ws/events/event-1/attendance"), response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(attributes).isEmpty();
        verify404();
    }

    @Test
    void anUnparseableScopeIsRefusedWithoutAskingWhoIsCalling() {
        boolean allowed =
                interceptor.beforeHandshake(requestTo("/ws/events/event-1"), response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(attributes).isEmpty();
        verifyNoInteractions(identityAccessModule, authorizer);
        verify404();
    }

    @Test
    void afterHandshakeDecidesNothing() {
        ServerHttpRequest request = requestTo("/ws/events/event-1/attendance");

        interceptor.afterHandshake(request, response, null, null);

        verifyNoInteractions(response, identityAccessModule, authorizer);
    }

    private void verify404() {
        verify(response).setStatusCode(HttpStatus.NOT_FOUND);
    }

    private static ServerHttpRequest requestTo(String path) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://campus.example" + path));
        return request;
    }
}
