package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

class DoorSocketHandlerTest {

    private final DoorScopeSessions sessions = new DoorScopeSessions();
    private final DoorSocketHandler handler = new DoorSocketHandler(sessions);

    @Test
    void aSocketJoinsTheScopeItsHandshakeWasAuthorizedFor() {
        WebSocketSession session = sessionInScope("event-1");

        handler.afterConnectionEstablished(session);

        // Registered wrapped rather than raw, so that one slow reader cannot hold up the check-in
        // request whose write triggered the fan-out.
        assertThat(sessions.inScope("event-1"))
                .singleElement()
                .isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
    }

    @Test
    void aClosedSocketLeavesTheScope() {
        WebSocketSession session = sessionInScope("event-1");
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(sessions.inScope("event-1")).isEmpty();
    }

    @Test
    void aSocketWithNoAuthorizedScopeJoinsNothing() {
        // Unreachable while the interceptor is the only way in, and that is the point: the attribute is
        // the interceptor's "yes", so a session without one is never treated as subscribed to anything.
        WebSocketSession unauthorized = mock(WebSocketSession.class);
        when(unauthorized.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(unauthorized);
        handler.afterConnectionClosed(unauthorized, CloseStatus.NORMAL);

        assertThat(sessions.inScope("event-1")).isEmpty();
    }

    @Test
    void whateverAClientSendsIsDropped() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"attendance-changed\"}"));

        // The channel is one-way. A client cannot make this server do anything by talking back — not
        // even echo a hint to the other screens on the same door.
        verifyNoInteractions(session);
    }

    private static WebSocketSession sessionInScope(String eventId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DoorSocketHandler.SCOPE_ATTRIBUTE, eventId);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("session-1");
        return session;
    }
}
