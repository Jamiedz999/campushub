package com.campushub.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// Every row of the Phase table in docs/adr/03-define-event-lifecycle.md, including the exact instants
// at which one Phase gives way to the next. All against a fixed set of timestamps and a fixed `now` —
// no wall clock anywhere, per the technical baseline's Clock rule.
class PhaseTest {

    private static final Instant OPENS = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant CLOSES = Instant.parse("2026-03-10T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-03-20T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-20T02:00:00Z");
    private static final int CAPACITY = 2;

    @Test
    void draftStatusIsAlwaysDraftPhaseRegardlessOfTime() {
        Event event = draft();

        assertThat(Phase.of(event, ENDS.plusSeconds(1_000_000))).isEqualTo(Phase.DRAFT);
    }

    @Test
    void cancelledStatusIsAlwaysCancelledPhaseRegardlessOfTime() {
        Event event = published(List.of());

        assertThat(Phase.of(cancel(event), STARTS)).isEqualTo(Phase.CANCELLED);
    }

    @Test
    void beforeRegistrationOpensIsScheduled() {
        Event event = published(List.of());

        assertThat(Phase.of(event, OPENS.minusSeconds(1))).isEqualTo(Phase.SCHEDULED);
    }

    @Test
    void atTheExactInstantRegistrationOpensPhaseBecomesRegistrationOpen() {
        Event event = published(List.of());

        assertThat(Phase.of(event, OPENS)).isEqualTo(Phase.REGISTRATION_OPEN);
    }

    @Test
    void withinTheWindowWithFreeSeatsIsRegistrationOpen() {
        Event event = published(List.of("student-1"));

        assertThat(Phase.of(event, OPENS.plusSeconds(60))).isEqualTo(Phase.REGISTRATION_OPEN);
    }

    @Test
    void withinTheWindowWithNoFreeSeatsIsFull() {
        Event event = published(List.of("student-1", "student-2"));

        assertThat(Phase.of(event, OPENS.plusSeconds(60))).isEqualTo(Phase.FULL);
    }

    @Test
    void fullResolvesTheMomentASeatFreesUp() {
        Event fullEvent = published(List.of("student-1", "student-2"));
        Event afterWithdrawal = published(List.of("student-1"));

        assertThat(Phase.of(fullEvent, OPENS.plusSeconds(60))).isEqualTo(Phase.FULL);
        assertThat(Phase.of(afterWithdrawal, OPENS.plusSeconds(60))).isEqualTo(Phase.REGISTRATION_OPEN);
    }

    @Test
    void atTheExactInstantRegistrationClosesPhaseBecomesRegistrationClosed() {
        Event event = published(List.of());

        assertThat(Phase.of(event, CLOSES)).isEqualTo(Phase.REGISTRATION_CLOSED);
    }

    @Test
    void betweenClosingAndStartingIsRegistrationClosed() {
        Event event = published(List.of());

        assertThat(Phase.of(event, CLOSES.plusSeconds(60))).isEqualTo(Phase.REGISTRATION_CLOSED);
    }

    @Test
    void aFullEventPastClosingIsRegistrationClosedNotFull() {
        Event event = published(List.of("student-1", "student-2"));

        assertThat(Phase.of(event, CLOSES.plusSeconds(60))).isEqualTo(Phase.REGISTRATION_CLOSED);
    }

    @Test
    void atTheExactInstantTheEventStartsPhaseBecomesInProgress() {
        Event event = published(List.of());

        assertThat(Phase.of(event, STARTS)).isEqualTo(Phase.IN_PROGRESS);
    }

    @Test
    void betweenStartingAndEndingIsInProgress() {
        Event event = published(List.of());

        assertThat(Phase.of(event, STARTS.plusSeconds(60))).isEqualTo(Phase.IN_PROGRESS);
    }

    @Test
    void atTheExactInstantTheEventEndsPhaseBecomesCompleted() {
        Event event = published(List.of());

        assertThat(Phase.of(event, ENDS)).isEqualTo(Phase.COMPLETED);
    }

    @Test
    void afterEndingIsCompleted() {
        Event event = published(List.of());

        assertThat(Phase.of(event, ENDS.plusSeconds(60))).isEqualTo(Phase.COMPLETED);
    }

    private static Event draft() {
        return new Event("club-1", "Title", "Description", OPENS, CLOSES, STARTS, ENDS, CAPACITY);
    }

    private static Event published(List<String> enrolledStudentIds) {
        return withStatus(EventStatus.PUBLISHED, enrolledStudentIds);
    }

    private static Event cancel(Event event) {
        return withStatus(EventStatus.CANCELLED, event.getEnrolled());
    }

    private static Event withStatus(EventStatus status, List<String> enrolledStudentIds) {
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
                enrolledStudentIds,
                List.of());
    }
}
