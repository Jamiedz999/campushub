package com.campushub.event.domain;

import java.time.Instant;

// What a refused "Taking a Seat" write meant. Correctness lives entirely in EventRepository.takeSeat's
// guarded write; classifyFailure runs once, after that write already failed, purely to pick the message
// — see docs/adr/04-define-registration-capacity-and-waitlist.md's "Attempt first, then read the Event
// once to classify". If the Event's state moves between the write and this read, the worst outcome is a
// slightly stale explanation, never a wrong Seat. NOT_FOUND is not produced here — an Event that does
// not exist, or that a Student may not see at all (Draft), is decided by the caller before this method
// is reached.
public enum RegistrationOutcome {
    SUCCESS,
    NOT_FOUND,
    EVENT_CANCELLED,
    EVENT_STARTED,
    REGISTRATION_NOT_OPEN,
    REGISTRATION_CLOSED,
    ALREADY_ENROLLED,
    ALREADY_WAITLISTED,
    EVENT_FULL;

    public static RegistrationOutcome classifyFailure(Event event, String studentId, Instant now) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return EVENT_CANCELLED;
        }
        // Idempotency outranks every other reason: a double-clicked registration must always explain
        // itself as "already enrolled", even if the window has since closed or the Event has started.
        if (event.getEnrolled().stream().anyMatch(entry -> entry.studentId().equals(studentId))) {
            return ALREADY_ENROLLED;
        }
        if (event.getWaitlist().contains(studentId)) {
            return ALREADY_WAITLISTED;
        }
        if (!now.isBefore(event.getStartsAt())) {
            return EVENT_STARTED;
        }
        if (event.getStatus() != EventStatus.PUBLISHED || now.isBefore(event.getRegistrationOpensAt())) {
            return REGISTRATION_NOT_OPEN;
        }
        if (!now.isBefore(event.getRegistrationClosesAt())) {
            return REGISTRATION_CLOSED;
        }
        return EVENT_FULL;
    }
}
