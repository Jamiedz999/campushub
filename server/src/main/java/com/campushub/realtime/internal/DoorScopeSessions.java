package com.campushub.realtime.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Keyed by the session's own id rather than by the session object. What is registered is a wrapped
 * session, and what Spring hands back at close is the original — identity would fail to match, and the
 * scope would keep a socket nobody can write to for the life of the process.
 */
@Component
class DoorScopeSessions {

    private final Map<String, Map<String, WebSocketSession>> byEventId = new ConcurrentHashMap<>();

    void join(String eventId, WebSocketSession session) {
        byEventId.computeIfAbsent(eventId, key -> new ConcurrentHashMap<>()).put(session.getId(), session);
    }

    // The empty scope is dropped rather than left behind: a door that has closed for the night should
    // not cost this map an entry until the next restart.
    void leave(String eventId, String sessionId) {
        byEventId.computeIfPresent(eventId, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /** A snapshot, so a session leaving mid-fan-out cannot break the fan-out. */
    Collection<WebSocketSession> inScope(String eventId) {
        return List.copyOf(byEventId.getOrDefault(eventId, Map.of()).values());
    }
}
