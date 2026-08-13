package com.campushub.event;

import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventPage;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

// event owns the whole Event document — Status, the four timestamps, capacity and the Seat Ledger. See
// docs/adr/03-define-event-lifecycle.md and docs/planning/implementation/TECHNICAL-BASELINE.md.
//
// Every officer-facing method below takes the caller's officer Club grants as a parameter and scopes
// its query or guarded write with them — never load-then-check. See
// docs/adr/08-define-roles-and-resource-authorization.md. Callers (event.web) resolve those grants from
// identityaccess.CurrentActor first and are responsible for the create/edit/publish check that a create
// command has no existing resource to scope a query by (isOfficerOf the target Club).
public interface EventModule {

    /** Creates a Draft in {@code clubId} and returns its id. The caller must already be that Club's Officer. */
    String createDraft(
            String clubId,
            String title,
            String description,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant endsAt,
            int capacity);

    /** The Event, scoped to the caller's officer Clubs, whatever its Status. */
    Optional<Event> findForOfficer(String eventId, Set<String> callerOfficerClubIds);

    /** See docs/adr/03-define-event-lifecycle.md's "What may change, and when" — the guard lives in the write. */
    EventCommandResult edit(String eventId, Set<String> callerOfficerClubIds, EventEdit edit);

    /** Draft to Published only. Forward-only: there is no matching un-publish method. */
    EventCommandResult publish(String eventId, Set<String> callerOfficerClubIds);

    /** The owning Club's Officer. Published only, and only before endsAt. */
    EventCommandResult cancelAsOfficer(String eventId, Set<String> callerOfficerClubIds);

    /**
     * A University Admin, unscoped by Club — the one cross-Club write in the system. See
     * docs/adr/08-define-roles-and-resource-authorization.md.
     */
    EventCommandResult cancelAsAdmin(String eventId);

    /** Published Events only, matching docs/adr/16-define-event-discovery.md's search/filter/sort/paging. */
    EventPage browse(EventBrowseQuery query);
}
