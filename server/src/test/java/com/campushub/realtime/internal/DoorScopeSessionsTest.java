package com.campushub.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class DoorScopeSessionsTest {

    private final DoorScopeSessions sessions = new DoorScopeSessions();

    @Test
    void aScopeHoldsOnlyItsOwnSubscribers() {
        WebSocketSession firstDoor = mock(WebSocketSession.class);
        WebSocketSession secondDoor = mock(WebSocketSession.class);
        WebSocketSession otherEvent = mock(WebSocketSession.class);

        sessions.join("event-1", firstDoor);
        sessions.join("event-1", secondDoor);
        sessions.join("event-2", otherEvent);

        assertThat(sessions.inScope("event-1")).containsExactlyInAnyOrder(firstDoor, secondDoor);
        assertThat(sessions.inScope("event-2")).containsExactly(otherEvent);
        assertThat(sessions.inScope("event-3")).isEmpty();
    }

    @Test
    void aSessionThatLeavesStopsBeingWrittenTo() {
        WebSocketSession leaving = mock(WebSocketSession.class);
        WebSocketSession staying = mock(WebSocketSession.class);
        sessions.join("event-1", leaving);
        sessions.join("event-1", staying);

        sessions.leave("event-1", leaving);

        assertThat(sessions.inScope("event-1")).containsExactly(staying);
    }

    @Test
    void leavingIsSafeForAScopeOrASessionThatIsNotThere() {
        WebSocketSession never = mock(WebSocketSession.class);

        // Spring calls afterConnectionClosed for connections that never established, and a socket can
        // be closed twice. Neither is a condition this registry should have an opinion about.
        sessions.leave("event-1", never);
        sessions.join("event-1", never);
        sessions.leave("event-1", never);
        sessions.leave("event-1", never);

        assertThat(sessions.inScope("event-1")).isEmpty();
    }

    @Test
    void whatIsHandedToTheFanOutIsASnapshot() {
        WebSocketSession watching = mock(WebSocketSession.class);
        sessions.join("event-1", watching);

        var inScope = sessions.inScope("event-1");
        sessions.leave("event-1", watching);

        // A door screen closing mid-fan-out must not break the fan-out for the screen beside it.
        assertThat(inScope).containsExactly(watching);
        assertThat(sessions.inScope("event-1")).isEmpty();
    }
}
