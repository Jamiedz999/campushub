package com.campushub.realtime.internal;

import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.realtime.RealtimeModule.DoorScopeAuthorizer;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * The only place a subscription is authorized, and the reason it is enough: the handshake is an
 * ordinary HTTP request, so it arrives with the session behind the same filter chain as every other
 * route, and a socket that gets past here can never widen its own scope afterwards.
 *
 * <p>Refusal is <b>404, never 403</b>, like every other authorization failure in this system — the
 * query was scoped by the caller's grants, so another Club's door genuinely is not found. See
 * docs/adr/08-define-roles-and-resource-authorization.md.
 */
@Component
class DoorScopeHandshakeInterceptor implements HandshakeInterceptor {

    private final IdentityAccessModule identityAccessModule;
    private final DoorScopeAuthorizer authorizer;

    DoorScopeHandshakeInterceptor(IdentityAccessModule identityAccessModule, DoorScopeAuthorizer authorizer) {
        this.identityAccessModule = identityAccessModule;
        this.authorizer = authorizer;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Optional<String> eventId = DoorScopePath.eventIdIn(request.getURI().getPath());
        if (eventId.isEmpty() || !mayWatch(eventId.get())) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        attributes.put(DoorSocketHandler.SCOPE_ATTRIBUTE, eventId.get());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do: the scope was decided before the socket existed.
    }

    // Grants are resolved here rather than read from anything the session cached, so a revoked Officer
    // cannot open a new door screen — the same "fresh on every request" rule the HTTP paths follow. An
    // already-open socket is not re-checked; it dies with its scope at the end of the Event.
    private boolean mayWatch(String eventId) {
        return authorizer.mayWatchDoor(eventId, identityAccessModule.currentActor().officerClubIds());
    }
}
