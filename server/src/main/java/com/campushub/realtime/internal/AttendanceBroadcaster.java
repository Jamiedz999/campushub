package com.campushub.realtime.internal;

import com.campushub.realtime.RealtimeModule;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/**
 * The seam named in docs/planning/implementation/TECHNICAL-BASELINE.md, from the production side: a
 * real WebSocket fan-out here, and a recording no-op wherever a test wants to ask "did this write
 * publish a hint, exactly one, carrying what?" without a server, a client and a wait.
 *
 * <p>The interface it answers is {@link RealtimeModule} itself rather than a second one beside it. A
 * module whose whole job is to broadcast has nothing left to be a middle man between.
 */
@Component
class AttendanceBroadcaster implements RealtimeModule {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceBroadcaster.class);

    private final DoorScopeSessions sessions;
    private final ObjectMapper objectMapper;

    AttendanceBroadcaster(DoorScopeSessions sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishAttendanceChanged(String eventId) {
        TextMessage message =
                new TextMessage(objectMapper.writeValueAsString(AttendanceHint.attendanceChanged(eventId)));
        for (WebSocketSession session : sessions.inScope(eventId)) {
            send(session, message);
        }
    }

    // A hint that cannot be delivered is deliberately swallowed. The write it followed has already
    // succeeded and is not being undone for a dead socket, and the client that missed this frame
    // re-reads on reconnect — which is the same recovery it would use for a frame lost in the network,
    // where there would be nothing to catch at all.
    //
    // Nothing here blocks on a slow reader either: every session was wrapped at the handshake in a
    // decorator that buffers, so a projector on bad wifi cannot hold up the check-in request whose
    // write triggered this fan-out. See DoorSocketHandler.
    private void send(WebSocketSession session, TextMessage message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            LOG.debug("Dropped an attendance hint for a session that could not be written to", e);
        }
    }
}
