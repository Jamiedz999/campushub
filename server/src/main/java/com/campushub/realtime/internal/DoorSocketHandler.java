package com.campushub.realtime.internal;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * Registers a socket against the scope its handshake was authorized for, and forgets it when it goes.
 *
 * <p>The scope is read from the session attributes rather than from the session's URI, because the
 * attribute was put there by the handshake that checked it. Re-parsing the URI here would be reading
 * the same client-supplied string a second time and calling it authorized.
 */
@Component
class DoorSocketHandler extends AbstractWebSocketHandler {

    /** Written by {@link DoorScopeHandshakeInterceptor} once, and only when it said yes. */
    static final String SCOPE_ATTRIBUTE = "doorScopeEventId";

    private final DoorScopeSessions sessions;

    DoorSocketHandler(DoorScopeSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        scopeOf(session).ifPresent(eventId -> sessions.join(eventId, session));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        scopeOf(session).ifPresent(eventId -> sessions.leave(eventId, session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // The channel is one-way. A client has nothing to say that this server would act on, so an
        // inbound frame is dropped rather than parsed: an unread message cannot be a confused deputy.
    }

    private static Optional<String> scopeOf(WebSocketSession session) {
        Object scope = session.getAttributes().get(SCOPE_ATTRIBUTE);
        return scope instanceof String eventId ? Optional.of(eventId) : Optional.empty();
    }
}
