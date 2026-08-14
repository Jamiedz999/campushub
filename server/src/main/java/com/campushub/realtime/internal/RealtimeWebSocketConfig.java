package com.campushub.realtime.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// Raw WebSocket, no STOMP and no SockJS. A broker would give this channel topics, acknowledgements and
// a subscription protocol, all of which exist to make delivery reliable — and delivery here is
// deliberately unreliable, because the client's correctness comes from re-reading rather than from
// receiving. See docs/planning/implementation/TECHNICAL-BASELINE.md.
//
// The path sits outside /api deliberately. That prefix is the HTTP API's, whose contract — RFC 9457
// problem responses, the { items, page, size, total } envelope, plural-noun resources — describes
// requests and responses that this endpoint does not have. A socket is a connection, not a resource.
//
// Origins are left at Spring's default of same-origin only: the frontend is served from this same
// application, and a cross-origin socket would be a session ridden from another site — the thing CSRF
// protection stops on the HTTP side, where the handshake's GET is not otherwise covered.
@Configuration
@EnableWebSocket
class RealtimeWebSocketConfig implements WebSocketConfigurer {

    private final DoorSocketHandler doorSocketHandler;
    private final DoorScopeHandshakeInterceptor doorScopeHandshakeInterceptor;

    RealtimeWebSocketConfig(
            DoorSocketHandler doorSocketHandler, DoorScopeHandshakeInterceptor doorScopeHandshakeInterceptor) {
        this.doorSocketHandler = doorSocketHandler;
        this.doorScopeHandshakeInterceptor = doorScopeHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(doorSocketHandler, DoorScopePath.PATTERN)
                .addInterceptors(doorScopeHandshakeInterceptor);
    }
}
