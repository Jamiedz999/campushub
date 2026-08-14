package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class AttendanceBroadcasterTest {

    private final DoorScopeSessions sessions = new DoorScopeSessions();
    private final AttendanceBroadcaster broadcaster = new AttendanceBroadcaster(sessions, new ObjectMapper());

    @Test
    void everyOpenSubscriberOfTheScopeGetsTheHint() throws Exception {
        WebSocketSession door = openSession();
        WebSocketSession secondScreen = openSession();
        sessions.join("event-1", door);
        sessions.join("event-1", secondScreen);

        broadcaster.publishAttendanceChanged("event-1");

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(door).sendMessage(sent.capture());
        verify(secondScreen).sendMessage(any(TextMessage.class));
        assertThat(sent.getValue().getPayload())
                .isEqualTo("{\"type\":\"attendance-changed\",\"eventId\":\"event-1\"}");
    }

    @Test
    void aScreenWatchingAnotherEventHearsNothing() throws Exception {
        WebSocketSession otherDoor = openSession();
        sessions.join("event-2", otherDoor);

        broadcaster.publishAttendanceChanged("event-1");

        // The scope is the whole authorization story after the handshake: a hint fanned out to every
        // socket would tell one Club's Officer exactly when another Club's door was busy.
        verify(otherDoor, never()).sendMessage(any());
    }

    @Test
    void aClosedSessionIsNotWrittenTo() throws Exception {
        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.getId()).thenReturn("closed-session");
        when(closed.isOpen()).thenReturn(false);
        sessions.join("event-1", closed);

        broadcaster.publishAttendanceChanged("event-1");

        verify(closed, never()).sendMessage(any());
    }

    @Test
    void aSocketThatFailsMidSendCannotFailTheAttendanceWriteBehindIt() throws Exception {
        WebSocketSession broken = openSession();
        doThrow(new IOException("connection reset")).when(broken).sendMessage(any());
        WebSocketSession healthy = openSession();
        sessions.join("event-1", broken);
        sessions.join("event-1", healthy);

        // The Student is already checked in by the time this runs. A dead projector cannot un-check
        // them in, and the screen behind it converges by re-reading when it reconnects.
        assertThatNoException().isThrownBy(() -> broadcaster.publishAttendanceChanged("event-1"));
        verify(healthy).sendMessage(any());
    }

    private static WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
