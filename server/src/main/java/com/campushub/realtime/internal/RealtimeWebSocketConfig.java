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
