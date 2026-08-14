package com.campushub.event.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.SlotCommandOutcome;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.EventNotEditableException;
import com.campushub.shared.NotFoundException;
import com.campushub.venue.VenueModule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VenueSlotControllerTest {

    private static final Instant START = Instant.parse("2026-03-20T10:00:00Z");
    private static final Instant END = Instant.parse("2026-03-20T11:00:00Z");

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private EventModule eventModule;

    private VenueSlotController controller;

    @BeforeEach
    void setUp() {
        controller = new VenueSlotController(identityAccessModule, eventModule);
    }

    @Test
    void anOfficerBooksThroughTheClubScopedPath() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.bookSlotAsOfficer("event-1", Set.of("club-a"), "venue-1", START, END))
                .thenReturn(SlotCommandOutcome.SUCCESS);

        controller.book("event-1", new BookSlotRequest("venue-1", START, END));

        verify(eventModule).bookSlotAsOfficer("event-1", Set.of("club-a"), "venue-1", START, END);
        verify(eventModule, never()).bookSlotAsAdmin("event-1", "venue-1", START, END);
    }

    @Test
    void aUniversityAdminBooksThroughTheUnscopedPath() {
        when(identityAccessModule.currentActor()).thenReturn(admin());
        when(eventModule.bookSlotAsAdmin("event-1", "venue-1", START, END))
                .thenReturn(SlotCommandOutcome.SUCCESS);

        controller.book("event-1", new BookSlotRequest("venue-1", START, END));

        verify(eventModule).bookSlotAsAdmin("event-1", "venue-1", START, END);
    }

    @Test
    void aLostAtomicWriteReturnsTheStableSlotTakenCode() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.bookSlotAsOfficer("event-1", Set.of("club-a"), "venue-1", START, END))
                .thenReturn(SlotCommandOutcome.SLOT_TAKEN);

        assertThatThrownBy(() -> controller.book("event-1", new BookSlotRequest("venue-1", START, END)))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).code())
                .isEqualTo(ErrorCode.SLOT_TAKEN);
    }

    @ParameterizedTest
    @MethodSource("slotValidationFailures")
    void everySlotValidationFailureKeepsItsStableCode(
            SlotCommandOutcome outcome, ErrorCode code) {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.bookSlotAsOfficer("event-1", Set.of("club-a"), "venue-1", START, END))
                .thenReturn(outcome);

        assertThatThrownBy(() -> controller.book("event-1", new BookSlotRequest("venue-1", START, END)))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).code())
                .isEqualTo(code);
    }

    @Test
    void missingAndNonEditableSlotCommandsKeepTheirExistingProblemTypes() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.bookSlotAsOfficer("missing", Set.of("club-a"), "venue-1", START, END))
                .thenReturn(SlotCommandOutcome.NOT_FOUND);
        when(eventModule.bookSlotAsOfficer("closed", Set.of("club-a"), "venue-1", START, END))
                .thenReturn(SlotCommandOutcome.NOT_EDITABLE);

        assertThatThrownBy(() -> controller.book("missing", new BookSlotRequest("venue-1", START, END)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> controller.book("closed", new BookSlotRequest("venue-1", START, END)))
                .isInstanceOf(EventNotEditableException.class);
    }

    @Test
    void releaseUsesTheOfficerScopedOrAdminPath() {
        when(identityAccessModule.currentActor()).thenReturn(officer(), admin());
        when(eventModule.releaseSlotAsOfficer("event-1", Set.of("club-a")))
                .thenReturn(SlotCommandOutcome.SUCCESS);
        when(eventModule.releaseSlotAsAdmin("event-1")).thenReturn(SlotCommandOutcome.SUCCESS);

        controller.release("event-1");
        controller.release("event-1");

        verify(eventModule).releaseSlotAsOfficer("event-1", Set.of("club-a"));
        verify(eventModule).releaseSlotAsAdmin("event-1");
    }

    @Test
    void anOfficerCanReadTheSelfHealingVenueDayTimeline() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        VenueModule.VenueDayView day = new VenueModule.VenueDayView(
                new VenueModule.VenueSummary("venue-1", "Hall"),
                LocalDate.parse("2026-03-20"),
                List.of(new VenueModule.DayBooking("event-1", 600, 660)));
        when(eventModule.findVenueDay("venue-1", LocalDate.parse("2026-03-20")))
                .thenReturn(Optional.of(day));

        VenueDayResponse response =
                controller.day("venue-1", LocalDate.parse("2026-03-20"));

        assertThat(response.venue().id()).isEqualTo("venue-1");
        assertThat(response.bookings())
                .extracting(VenueDayResponse.Booking::eventId)
                .containsExactly("event-1");
    }

    @Test
    void aStudentCannotReadTheOfficerTimeline() {
        when(identityAccessModule.currentActor()).thenReturn(student());

        assertThatThrownBy(() -> controller.day("venue-1", LocalDate.parse("2026-03-20")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anUnknownVenueTimelineIsNotFound() {
        when(identityAccessModule.currentActor()).thenReturn(admin());
        when(eventModule.findVenueDay("missing", LocalDate.parse("2026-03-20")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.day("missing", LocalDate.parse("2026-03-20")))
                .isInstanceOf(NotFoundException.class);
    }

    private static Stream<Arguments> slotValidationFailures() {
        return Stream.of(
                Arguments.of(SlotCommandOutcome.SLOT_CROSSES_MIDNIGHT, ErrorCode.SLOT_CROSSES_MIDNIGHT),
                Arguments.of(SlotCommandOutcome.SLOT_IN_DST_TRANSITION, ErrorCode.SLOT_IN_DST_TRANSITION),
                Arguments.of(SlotCommandOutcome.SLOT_ALREADY_STARTED, ErrorCode.SLOT_ALREADY_STARTED));
    }

    private static CurrentActor admin() {
        return new CurrentActor("admin", "admin@example.edu", "Admin", SystemRole.UNIVERSITY_ADMIN, Set.of());
    }

    private static CurrentActor officer() {
        return new CurrentActor("officer", "officer@example.edu", "Officer", SystemRole.STUDENT, Set.of("club-a"));
    }

    private static CurrentActor student() {
        return new CurrentActor("student", "student@example.edu", "Student", SystemRole.STUDENT, Set.of());
    }
}
