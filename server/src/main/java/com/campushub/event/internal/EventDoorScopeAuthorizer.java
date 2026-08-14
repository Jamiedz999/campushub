package com.campushub.event.internal;

import com.campushub.event.EventModule;
import com.campushub.realtime.RealtimeModule.DoorScopeAuthorizer;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Answers the socket's one question with the door screen's own scoped query, so a subscription is
 * entitled on exactly the same terms as the HTTP door code it sits beside — one rule, one query, no
 * second definition of "this Officer's door" to drift out of step with the first.
 */
@Component
class EventDoorScopeAuthorizer implements DoorScopeAuthorizer {

    private final EventModule eventModule;

    EventDoorScopeAuthorizer(EventModule eventModule) {
        this.eventModule = eventModule;
    }

    @Override
    public boolean mayWatchDoor(String eventId, Set<String> callerOfficerClubIds) {
        return eventModule.findDoorEventForOfficer(eventId, callerOfficerClubIds).isPresent();
    }
}
