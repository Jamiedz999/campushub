package com.campushub.realtime;

import java.util.Set;

/**
 * The one-way channel from the server to a door screen, and nothing else. See
 * docs/adr/07-define-qr-checkin-and-anti-fraud.md and
 * docs/planning/implementation/TECHNICAL-BASELINE.md.
 *
 * <p><b>The socket carries a refresh hint, never authoritative state.</b> A hint says only that
 * something changed in a scope the client is subscribed to; the client then re-reads an authorized
 * snapshot over ordinary HTTP. That is what makes a dropped connection harmless: a client that missed
 * every message in between re-reads on reconnect and lands on the same answer as one that missed none.
 * A count pushed down this channel would have neither property — it would be stale after the message
 * that got lost, and it would be state the socket authorized rather than the HTTP read did.
 *
 * <p>The fan-out is <b>in-process</b>, which is correct for the single instance Core deploys and wrong
 * for several: a second instance would hold its own sessions and never hear the first one's writes.
 * Horizontal scale needs a shared broker, and that is a change to the baseline rather than a local one.
 */
public interface RealtimeModule {

    /**
     * Tells every subscriber of this Event's door scope to re-read. Called by whoever wrote the
     * attendance, after the write has already succeeded — the hint is a consequence of the write, never
     * a participant in it, so a socket that fails can never fail a check-in.
     */
    void publishAttendanceChanged(String eventId);

    /**
     * Decides who may watch one Event's door scope, implemented by the module that owns the Event
     * document.
     *
     * <p>The direction is deliberate. Whether an account is this Club's Officer is a fact about the
     * Event, and asking {@code event} for it directly would make {@code realtime} depend on the module
     * that has to depend on {@code realtime} to publish — a cycle the build rejects. Inverting the one
     * question keeps the arrow pointing one way, and keeps the socket ignorant of what a Club is.
     *
     * <p>The implementation must <b>scope its query</b> by the caller's grants rather than load an
     * Event and then compare, exactly as every HTTP path does. See
     * docs/adr/08-define-roles-and-resource-authorization.md.
     */
    interface DoorScopeAuthorizer {

        boolean mayWatchDoor(String eventId, Set<String> callerOfficerClubIds);
    }
}
