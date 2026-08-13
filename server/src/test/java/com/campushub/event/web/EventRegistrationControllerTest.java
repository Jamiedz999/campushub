package com.campushub.event.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.WithdrawalOutcome;
import com.campushub.event.domain.EnrolledEntry;
import com.campushub.event.domain.EnrollmentVia;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventStatus;
import com.campushub.event.domain.RegistrationOutcome;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import com.campushub.shared.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventRegistrationControllerTest {

    private static final Instant NOW = Instant.parse("2026-03-05T00:00:00Z");
    private static final CurrentActor STUDENT =
            new CurrentActor("student-1", "student@demo.campushub", "Student", SystemRole.STUDENT, java.util.Set.of());

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private EventModule eventModule;

    private EventRegistrationController controller;

    @BeforeEach
    void setUp() {
        controller =
                new EventRegistrationController(identityAccessModule, eventModule, Clock.fixed(NOW, ZoneOffset.UTC));
        when(identityAccessModule.currentActor()).thenReturn(STUDENT);
    }

    @Test
    void getReturnsTheStudentsViewOfAVisibleEvent() {
        when(eventModule.findForStudent("event-1")).thenReturn(Optional.of(someEvent()));

        EventRegistrationView view = controller.get("event-1");

        assertThat(view.title()).isEqualTo("Title");
        assertThat(view.enrolled()).isFalse();
    }

    @Test
    void getShowsAStudentsOneBasedWaitlistPosition() {
        Event event = studentEvent(List.of(), List.of("student-a", "student-1", "student-b"));
        when(eventModule.findForStudent("event-1")).thenReturn(Optional.of(event));

        EventRegistrationView view = controller.get("event-1");

        assertThat(view.waitlistPosition()).isEqualTo(2);
        assertThat(view.enrollmentVia()).isNull();
    }

    @Test
    void getCarriesThePromotionSignalWithoutExposingOtherStudents() {
        Event event = studentEvent(
                List.of(new EnrolledEntry("student-1", EnrollmentVia.PROMOTED, NOW.minusSeconds(1))),
                List.of("student-b"));
        when(eventModule.findForStudent("event-1")).thenReturn(Optional.of(event));

        EventRegistrationView view = controller.get("event-1");

        assertThat(view.enrolled()).isTrue();
        assertThat(view.enrollmentVia()).isEqualTo(EnrollmentVia.PROMOTED);
        assertThat(view.waitlistPosition()).isNull();
    }

    @Test
    void getThrowsNotFoundWhenTheEventIsNotVisibleToStudents() {
        when(eventModule.findForStudent("event-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get("event-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void registerReturnsTheUpdatedViewOnSuccess() {
        when(eventModule.register("event-1", "student-1")).thenReturn(RegistrationOutcome.SUCCESS);
        when(eventModule.findForStudent("event-1")).thenReturn(Optional.of(someEvent()));

        EventRegistrationView view = controller.register("event-1");

        assertThat(view.title()).isEqualTo("Title");
    }

    @Test
    void registerThrowsNotFoundWhenTheOutcomeIsNotFound() {
        when(eventModule.register("event-1", "student-1")).thenReturn(RegistrationOutcome.NOT_FOUND);

        assertThatThrownBy(() -> controller.register("event-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void registerThrowsAConflictWithEventCancelledWhenTheEventWasCancelled() {
        assertConflict(RegistrationOutcome.EVENT_CANCELLED, ErrorCode.EVENT_CANCELLED);
    }

    @Test
    void registerThrowsAConflictWithEventStartedWhenTheFreezeHasEngaged() {
        assertConflict(RegistrationOutcome.EVENT_STARTED, ErrorCode.EVENT_STARTED);
    }

    @Test
    void registerThrowsAConflictWithRegistrationNotOpenBeforeTheWindowOpens() {
        assertConflict(RegistrationOutcome.REGISTRATION_NOT_OPEN, ErrorCode.REGISTRATION_NOT_OPEN);
    }

    @Test
    void registerThrowsAConflictWithRegistrationClosedAfterTheWindowCloses() {
        assertConflict(RegistrationOutcome.REGISTRATION_CLOSED, ErrorCode.REGISTRATION_CLOSED);
    }

    @Test
    void registerThrowsAConflictWithAlreadyEnrolledOnADoubleClick() {
        assertConflict(RegistrationOutcome.ALREADY_ENROLLED, ErrorCode.ALREADY_ENROLLED);
    }

    @Test
    void registerThrowsAConflictWithAlreadyWaitlistedWhenAlreadyQueued() {
        assertConflict(RegistrationOutcome.ALREADY_WAITLISTED, ErrorCode.ALREADY_WAITLISTED);
    }

    @Test
    void registerThrowsAConflictWithEventFullWhenCapacityIsReached() {
        assertConflict(RegistrationOutcome.EVENT_FULL, ErrorCode.EVENT_FULL);
    }

    @Test
    void withdrawReturnsTheUpdatedViewOnSuccess() {
        when(eventModule.withdraw("event-1", "student-1")).thenReturn(WithdrawalOutcome.SUCCESS);
        when(eventModule.findForStudent("event-1")).thenReturn(Optional.of(someEvent()));

        EventRegistrationView view = controller.withdraw("event-1");

        assertThat(view.title()).isEqualTo("Title");
    }

    @Test
    void withdrawThrowsAConflictWithEventStartedWhenTheFreezeHasEngaged() {
        when(eventModule.withdraw("event-1", "student-1")).thenReturn(WithdrawalOutcome.EVENT_STARTED);

        assertThatThrownBy(() -> controller.withdraw("event-1"))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).code())
                .isEqualTo(ErrorCode.EVENT_STARTED);
    }

    @Test
    void withdrawThrowsAConflictWhenTheEventWasCancelled() {
        when(eventModule.withdraw("event-1", "student-1")).thenReturn(WithdrawalOutcome.EVENT_CANCELLED);

        assertThatThrownBy(() -> controller.withdraw("event-1"))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).code())
                .isEqualTo(ErrorCode.EVENT_CANCELLED);
    }

    @Test
    void withdrawThrowsNotFoundWhenTheEventIsNotVisible() {
        when(eventModule.withdraw("event-1", "student-1")).thenReturn(WithdrawalOutcome.NOT_FOUND);

        assertThatThrownBy(() -> controller.withdraw("event-1")).isInstanceOf(NotFoundException.class);
    }

    private void assertConflict(RegistrationOutcome outcome, ErrorCode expectedCode) {
        when(eventModule.register("event-1", "student-1")).thenReturn(outcome);

        assertThatThrownBy(() -> controller.register("event-1"))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).code())
                .isEqualTo(expectedCode);
    }

    @Test
    void mineReturnsAPageBuiltFromTheStudentsEnrolments() {
        EventPage page = new EventPage(List.of(someEvent()), 0, 20, 1);
        when(eventModule.findEnrolled("student-1", 0, 20)).thenReturn(page);

        PageResponse<EventRegistrationView> response = controller.mine(0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("Title");
        assertThat(response.total()).isEqualTo(1);
    }

    private static Event someEvent() {
        return new Event(
                "club-a", "Title", "Description", NOW, NOW.plusSeconds(10), NOW.plusSeconds(20),
                NOW.plusSeconds(30), 5);
    }

    private static Event studentEvent(List<EnrolledEntry> enrolled, List<String> waitlist) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("event-1");
        when(event.getClubId()).thenReturn("club-a");
        when(event.getTitle()).thenReturn("Title");
        when(event.getDescription()).thenReturn("Description");
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getRegistrationOpensAt()).thenReturn(NOW.minusSeconds(10));
        when(event.getRegistrationClosesAt()).thenReturn(NOW.plusSeconds(10));
        when(event.getStartsAt()).thenReturn(NOW.plusSeconds(20));
        when(event.getEndsAt()).thenReturn(NOW.plusSeconds(30));
        when(event.getCapacity()).thenReturn(5);
        when(event.getEnrolled()).thenReturn(enrolled);
        when(event.getWaitlist()).thenReturn(waitlist);
        return event;
    }
}
