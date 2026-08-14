package com.campushub.realtime.internal;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Who is currently watching which door. In-process and in-memory, because the sessions themselves are:
 * a socket cannot outlive the instance holding it, so a registry that outlived the instance would only
 * be a list of connections nobody can write to.
 *
 * <p>Nothing here survives a restart, and nothing needs to. A screen whose socket dies reconnects and
 * re-reads, which is the same path it takes after any missed message.
 */
@Component
class DoorScopeSessions {

    private final Map<String, Set<WebSocketSession>> byEventId = new ConcurrentHashMap<>();

    void join(String eventId, WebSocketSession session) {
        byEventId.computeIfAbsent(eventId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    // The empty set is dropped rather than left behind: a door that has closed for the night should not
    // cost this map an entry until the next restart.
    void leave(String eventId, WebSocketSession session) {
        byEventId.computeIfPresent(eventId, (key, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /** A snapshot, so a session leaving mid-fan-out cannot break the fan-out. */
    Set<WebSocketSession> inScope(String eventId) {
        return Set.copyOf(byEventId.getOrDefault(eventId, Set.of()));
    }
}
