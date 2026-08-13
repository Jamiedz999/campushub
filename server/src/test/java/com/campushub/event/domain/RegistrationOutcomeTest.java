package com.campushub.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// classifyFailure only ever runs after the guarded takeSeat write already failed — see
// docs/adr/04-define-registration-capacity-and-waitlist.md's "Attempt first, then read the Event once
// to classify". It never changes what happened, only which message the Student gets, so every case here
// is a pure function over a fixed Event and a fixed `now` — no wall clock, no Mongo.
class RegistrationOutcomeTest {

    private static final Instant OPENS = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant CLOSES = Instant.parse("2026-03-10T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-03-20T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-20T02:00:00Z");
    private static final int CAPACITY = 2;

    @Test
    void aCancelledEventIsAlwaysEventCancelledRegardlessOfMembershipOrTime() {
        Event event = withStatus(EventStatus.CANCELLED, List.of("student-1"), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", OPENS.plusSeconds(60)))
                .isEqualTo(RegistrationOutcome.EVENT_CANCELLED);
    }

    @Test
    void anAlreadyEnrolledStudentIsAlreadyEnrolledEvenPastStartsAt() {
        Event event = withStatus(EventStatus.PUBLISHED, List.of("student-1"), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", STARTS.plusSeconds(60)))
                .isEqualTo(RegistrationOutcome.ALREADY_ENROLLED);
    }

    @Test
    void cancellationOutranksAnExistingEnrolmentAsTheExplanation() {
        Event event = withStatus(EventStatus.CANCELLED, List.of("student-1"), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", OPENS.plusSeconds(60)))
                .isEqualTo(RegistrationOutcome.EVENT_CANCELLED);
    }

    @Test
    void anAlreadyWaitlistedStudentIsAlreadyWaitlisted() {
        Event event = withStatus(EventStatus.PUBLISHED, List.of(), List.of("student-1"));

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", OPENS.plusSeconds(60)))
                .isEqualTo(RegistrationOutcome.ALREADY_WAITLISTED);
    }

    @Test
    void atTheExactInstantTheEventStartsRegistrationIsRefusedAsEventStarted() {
        Event event = withStatus(EventStatus.PUBLISHED, List.of(), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", STARTS))
                .isEqualTo(RegistrationOutcome.EVENT_STARTED);
    }

    @Test
    void afterTheEventStartsRegistrationIsRefusedAsEventStarted() {
        Event event = withStatus(EventStatus.PUBLISHED, List.of(), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", STARTS.plusSeconds(60)))
                .isEqualTo(RegistrationOutcome.EVENT_STARTED);
    }

    @Test
    void beforeRegistrationOpensIsRegistrationNotOpen() {
        Event event = withStatus(EventStatus.PUBLISHED, List.of(), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", OPENS.minusSeconds(1)))
                .isEqualTo(RegistrationOutcome.REGISTRATION_NOT_OPEN);
    }

    @Test
    void aDraftEventIsRegistrationNotOpenEvenWithinWhatWouldBeTheWindow() {
        Event event = withStatus(EventStatus.DRAFT, List.of(), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", OPENS.plusSeconds(60)))
                .isEqualTo(RegistrationOutcome.REGISTRATION_NOT_OPEN);
    }

    @Test
    void atTheExactInstantRegistrationClosesItIsRegistrationClosed() {
        Event event = withStatus(EventStatus.PUBLISHED, List.of(), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", CLOSES))
                .isEqualTo(RegistrationOutcome.REGISTRATION_CLOSED);
    }

    @Test
    void betweenClosingAndStartingItIsRegistrationClosed() {
        Event event = withStatus(EventStatus.PUBLISHED, List.of(), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-1", CLOSES.plusSeconds(60)))
                .isEqualTo(RegistrationOutcome.REGISTRATION_CLOSED);
    }

    @Test
    void withinTheOpenWindowWithNoOtherReasonTheExplanationDefaultsToEventFull() {
        Event event = withStatus(EventStatus.PUBLISHED, List.of("student-1", "student-2"), List.of());

        assertThat(RegistrationOutcome.classifyFailure(event, "student-3", OPENS.plusSeconds(60)))
                .isEqualTo(RegistrationOutcome.EVENT_FULL);
    }

    private static Event withStatus(EventStatus status, List<String> enrolledStudentIds, List<String> waitlist) {
        return new Event(
                "event-1",
                "club-1",
                "Title",
                "Description",
                status,
                OPENS,
                CLOSES,
                STARTS,
                ENDS,
                CAPACITY,
                enrolledStudentIds.stream()
                        .map(studentId -> new EnrolledEntry(studentId, EnrollmentVia.DIRECT, OPENS))
                        .toList(),
                waitlist);
    }
}
