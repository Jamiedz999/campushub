package com.campushub.event.web;

import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventStatus;
import com.campushub.event.domain.Phase;
import java.time.Instant;

// The Club Officer's view of their own Event: the stored Status alongside the derived Phase, and Seat
// Ledger counts rather than the raw enrolled/waitlist id lists — see
// docs/adr/08-define-roles-and-resource-authorization.md ("a Student sees counts, never lists"; no
// student-facing roster of names exists yet in Core, so an Officer gets the same counts for now).
record EventOfficerResponse(
        String id,
        String clubId,
        String title,
        String description,
        EventStatus status,
        Phase phase,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int enrolledCount,
        int waitlistCount,
        int promotedCount,
        int everQueuedCount,
        double waitlistConversion) {

    static EventOfficerResponse from(Event event, Instant now) {
        return new EventOfficerResponse(
                event.getId(),
                event.getClubId(),
                event.getTitle(),
                event.getDescription(),
                event.getStatus(),
                Phase.of(event, now),
                event.getRegistrationOpensAt(),
                event.getRegistrationClosesAt(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCapacity(),
                event.getEnrolled().size(),
                event.getWaitlist().size(),
                event.getPromotedCount(),
                event.getEverQueuedCount(),
                event.waitlistConversion());
    }
}
