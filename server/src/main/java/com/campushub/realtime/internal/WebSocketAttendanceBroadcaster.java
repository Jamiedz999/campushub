package com.campushub.realtime.internal;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/** The production fan-out: one text frame per open session in the scope, and no reply expected. */
@Component
class WebSocketAttendanceBroadcaster implements AttendanceBroadcaster {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketAttendanceBroadcaster.class);

    private final DoorScopeSessions sessions;
    private final ObjectMapper objectMapper;

    WebSocketAttendanceBroadcaster(DoorScopeSessions sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    public void attendanceChanged(String eventId) {
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
    private void send(WebSocketSession session, TextMessage message) {
        try {
            // Spring's session is not safe for concurrent sends, and two attendance writes landing at
            // once is the ordinary case at a door.
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException e) {
            LOG.debug("Dropped an attendance hint for a session that could not be written to", e);
        }
    }
}
