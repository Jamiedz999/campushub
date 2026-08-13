package com.campushub.event.domain;

import java.time.Instant;

// Derived, never stored — computed fresh from Status, the four timestamps and the Seat Ledger on every
// read. See docs/adr/03-define-event-lifecycle.md: a stored Phase can contradict the timestamps beside
// it, so nothing writes one. `now` must always come from the injected Clock, never Instant.now().
public enum Phase {
    DRAFT,
    SCHEDULED,
    REGISTRATION_OPEN,
    FULL,
    REGISTRATION_CLOSED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static Phase of(Event event, Instant now) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return CANCELLED;
        }
        if (event.getStatus() == EventStatus.DRAFT) {
            return DRAFT;
        }
        // Published from here on. Terminal-to-earliest so a boundary instant lands in the later phase.
        if (!now.isBefore(event.getEndsAt())) {
            return COMPLETED;
        }
        if (!now.isBefore(event.getStartsAt())) {
            return IN_PROGRESS;
        }
        if (!now.isBefore(event.getRegistrationClosesAt())) {
            return REGISTRATION_CLOSED;
        }
        if (now.isBefore(event.getRegistrationOpensAt())) {
            return SCHEDULED;
        }
        return event.getEnrolled().size() < event.getCapacity() ? REGISTRATION_OPEN : FULL;
    }
}
