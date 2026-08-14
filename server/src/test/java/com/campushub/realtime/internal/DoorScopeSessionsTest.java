package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

class DoorScopeSessionsTest {

    private final DoorScopeSessions sessions = new DoorScopeSessions();

    @Test
    void aScopeHoldsOnlyItsOwnSubscribers() {
        WebSocketSession firstDoor = session("first");
        WebSocketSession secondDoor = session("second");
        WebSocketSession otherEvent = session("other");

        sessions.join("event-1", firstDoor);
        sessions.join("event-1", secondDoor);
        sessions.join("event-2", otherEvent);

        assertThat(sessions.inScope("event-1")).containsExactlyInAnyOrder(firstDoor, secondDoor);
        assertThat(sessions.inScope("event-2")).containsExactly(otherEvent);
        assertThat(sessions.inScope("event-3")).isEmpty();
    }

    @Test
    void aSessionThatLeavesStopsBeingWrittenTo() {
        WebSocketSession leaving = session("leaving");
        WebSocketSession staying = session("staying");
        sessions.join("event-1", leaving);
        sessions.join("event-1", staying);

        sessions.leave("event-1", "leaving");

        assertThat(sessions.inScope("event-1")).containsExactly(staying);
    }

    @Test
    void aWrappedSessionLeavesOnTheIdOfTheSocketItWraps() {
        // What is registered is the decorator the handler wrapped the socket in, and what Spring hands
        // back at close is the socket itself. Keyed by anything but the id, this scope would keep a
        // dead connection for the life of the process.
        WebSocketSession socket = session("socket-1");
        sessions.join("event-1", new ConcurrentWebSocketSessionDecorator(socket, 1_000, 1_024));

        sessions.leave("event-1", socket.getId());

        assertThat(sessions.inScope("event-1")).isEmpty();
    }

    @Test
    void leavingIsSafeForAScopeOrASessionThatIsNotThere() {
        WebSocketSession never = session("never");

        // Spring calls afterConnectionClosed for connections that never established, and a socket can
        // be closed twice. Neither is a condition this registry should have an opinion about.
        sessions.leave("event-1", "never");
        sessions.join("event-1", never);
        sessions.leave("event-1", "never");
        sessions.leave("event-1", "never");

        assertThat(sessions.inScope("event-1")).isEmpty();
    }

    @Test
    void whatIsHandedToTheFanOutIsASnapshot() {
        WebSocketSession watching = session("watching");
        sessions.join("event-1", watching);

        Collection<WebSocketSession> inScope = sessions.inScope("event-1");
        sessions.leave("event-1", "watching");

        // A door screen closing mid-fan-out must not break the fan-out for the screen beside it.
        assertThat(inScope).containsExactly(watching);
        assertThat(sessions.inScope("event-1")).isEmpty();
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
