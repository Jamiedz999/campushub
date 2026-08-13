package com.campushub.event.web;

import com.campushub.event.domain.Event;
import com.campushub.event.domain.EnrollmentVia;
import com.campushub.event.domain.Phase;
import java.time.Instant;

// The Student's view of an Event for the registration page: Phase and Seat Ledger counts, never the
// enrolled or waitlist id lists — same rule as EventBrowseItemResponse, see
// docs/adr/08-define-roles-and-resource-authorization.md. `enrolled` is the one addition: whether the
// calling Student themselves already holds a Seat, so the frontend can show "you're in" instead of a
// Register button without ever seeing who else is.
record EventRegistrationView(
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
        int waitlistCount,
        boolean enrolled,
        EnrollmentVia enrollmentVia,
        Integer waitlistPosition) {

    static EventRegistrationView from(Event event, String studentId, Instant now) {
        EnrollmentVia callerEnrollmentVia = event.getEnrolled().stream()
                .filter(entry -> entry.studentId().equals(studentId))
                .map(entry -> entry.via())
                .findFirst()
                .orElse(null);
        int callerWaitlistIndex = event.getWaitlist().indexOf(studentId);
        Integer callerWaitlistPosition = callerWaitlistIndex < 0 ? null : callerWaitlistIndex + 1;
        return new EventRegistrationView(
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
                event.getWaitlist().size(),
                callerEnrollmentVia != null,
                callerEnrollmentVia,
                callerWaitlistPosition);
    }
}
