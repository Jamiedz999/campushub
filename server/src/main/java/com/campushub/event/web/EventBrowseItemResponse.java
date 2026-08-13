package com.campushub.event.web;

import com.campushub.event.domain.Event;
import com.campushub.event.domain.Phase;
import java.time.Instant;

// The Student's view: Published Events only, Phase rather than Status (Status is an implementation
// detail — see docs/adr/03-define-event-lifecycle.md), and Seat Ledger counts, never the enrolled or
// waitlist id lists.
record EventBrowseItemResponse(
        String id,
        String clubId,
        String title,
        String description,
        Phase phase,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int enrolledCount,
        int waitlistCount) {

    static EventBrowseItemResponse from(Event event, Instant now) {
        return new EventBrowseItemResponse(
                event.getId(),
                event.getClubId(),
                event.getTitle(),
                event.getDescription(),
                Phase.of(event, now),
                event.getRegistrationOpensAt(),
                event.getRegistrationClosesAt(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCapacity(),
                event.getEnrolled().size(),
                event.getWaitlist().size());
    }
}
