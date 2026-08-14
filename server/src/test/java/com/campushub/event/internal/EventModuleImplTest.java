package com.campushub.event.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.event.EventModule.WithdrawalOutcome;
import com.campushub.event.EventModule.SeatRequestOutcome;
import com.campushub.event.EventModule.SeatRequestResult;
import com.campushub.event.EventModule.SlotCommandOutcome;
import com.campushub.event.EventModule.FormUpdateOutcome;
import com.campushub.event.EventModule.NumberField;
import com.campushub.event.EventModule.RegistrationForm;
import com.campushub.event.EventModule.ShortTextField;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventSort;
import com.campushub.event.domain.EventStatus;
import com.campushub.event.domain.RegistrationOutcome;
import com.campushub.event.persistence.EventRepository;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.FormValidationException;
import com.campushub.venue.VenueModule;
import com.campushub.venue.VenueModule.SlotRequestOutcome;
import com.campushub.venue.VenueModule.SlotRequestResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventModuleImplTest {

    private static final Instant NOW = Instant.parse("2026-03-05T00:00:00Z");
    private static final Set<String> CLUB_IDS = Set.of("club-a");

    @Mock
    private EventRepository repository;

    @Mock
    private VenueModule venueModule;

    private EventModuleImpl module;

    @BeforeEach
    void setUp() {
        module = new EventModuleImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC), venueModule);
    }

    @Test
    void createDraftDelegatesToTheRepositoryAndReturnsItsId() {
        when(repository.insertDraft(any(Event.class))).thenReturn("event-1");

        String id = module.createDraft(
                "club-a",
                "Title",
                "Description",
                NOW,
                NOW.plusSeconds(10),
                NOW.plusSeconds(20),
                NOW.plusSeconds(30),
                5);

        assertThat(id).isEqualTo("event-1");
    }

    @Test
    void findForOfficerDelegatesToTheRepository() {
        Event event = someEvent();
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));

        assertThat(module.findForOfficer("event-1", CLUB_IDS)).contains(event);
    }

    @Test
    void editReturnsSuccessWhenTheGuardedWriteMatches() {
        EventEdit edit = new EventEdit("New title", null, null, null, null, null, null);
        when(repository.edit("event-1", CLUB_IDS, edit, NOW)).thenReturn(true);

        assertThat(module.edit("event-1", CLUB_IDS, edit)).isEqualTo(EventCommandResult.SUCCESS);
    }

    @Test
    void editReturnsNotFoundWhenTheEventIsOutsideTheCallersClubGrants() {
        EventEdit edit = new EventEdit("New title", null, null, null, null, null, null);
        when(repository.edit("event-1", CLUB_IDS, edit, NOW)).thenReturn(false);
        when(repository.existsScoped("event-1", CLUB_IDS)).thenReturn(false);

        assertThat(module.edit("event-1", CLUB_IDS, edit)).isEqualTo(EventCommandResult.NOT_FOUND);
    }

    @Test
    void editReturnsNotEditableWhenTheEventExistsButTheLifecycleGuardRefused() {
        EventEdit edit = new EventEdit("New title", null, null, null, null, null, null);
        when(repository.edit("event-1", CLUB_IDS, edit, NOW)).thenReturn(false);
        when(repository.existsScoped("event-1", CLUB_IDS)).thenReturn(true);

        assertThat(module.edit("event-1", CLUB_IDS, edit)).isEqualTo(EventCommandResult.NOT_EDITABLE);
    }

    @Test
    void updateRegistrationFormReturnsSuccessWhenTheGuardedWriteMatches() {
        RegistrationForm form = new RegistrationForm(
                List.of(new ShortTextField("507f1f77bcf86cd799439011", "Preferred name", "", true, 80)));
        when(repository.updateRegistrationForm("event-1", CLUB_IDS, form, NOW)).thenReturn(true);

        assertThat(module.updateRegistrationForm("event-1", CLUB_IDS, form))
                .isEqualTo(FormUpdateOutcome.SUCCESS);
    }

    @Test
    void updateRegistrationFormClassifiesALockedEvent() {
        RegistrationForm form = RegistrationForm.empty();
        Event event = mock(Event.class);
        when(repository.updateRegistrationForm("event-1", CLUB_IDS, form, NOW)).thenReturn(false);
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));
        when(event.isRegistrationFormLocked()).thenReturn(true);

        assertThat(module.updateRegistrationForm("event-1", CLUB_IDS, form))
                .isEqualTo(FormUpdateOutcome.FORM_LOCKED);
    }

    @Test
    void updateRegistrationFormClassifiesMissingAndLifecycleFailures() {
        RegistrationForm form = RegistrationForm.empty();
        when(repository.updateRegistrationForm("missing", CLUB_IDS, form, NOW)).thenReturn(false);
        when(repository.findScopedById("missing", CLUB_IDS)).thenReturn(Optional.empty());
        Event event = mock(Event.class);
        when(repository.updateRegistrationForm("started", CLUB_IDS, form, NOW)).thenReturn(false);
        when(repository.findScopedById("started", CLUB_IDS)).thenReturn(Optional.of(event));

        assertThat(module.updateRegistrationForm("missing", CLUB_IDS, form))
                .isEqualTo(FormUpdateOutcome.NOT_FOUND);
        assertThat(module.updateRegistrationForm("started", CLUB_IDS, form))
                .isEqualTo(FormUpdateOutcome.NOT_EDITABLE);
    }

    @Test
    void updateRegistrationFormRejectsInvalidDefinitionsBeforeWriting() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("507f1f77bcf86cd799439011", "", "", true, 0),
                new NumberField(
                        "507f1f77bcf86cd799439011",
                        "Team size",
                        "",
                        false,
                        BigDecimal.TEN,
                        BigDecimal.ONE)));

        assertThatThrownBy(() -> module.updateRegistrationForm("event-1", CLUB_IDS, form))
                .isInstanceOf(FormValidationException.class)
                .extracting(exception -> ((FormValidationException) exception).code())
                .isEqualTo(ErrorCode.FORM_VALIDATION_FAILED);
        verify(repository, never()).updateRegistrationForm(any(), any(), any(), any());
    }

    @Test
    void publishReturnsSuccessWhenTheGuardedWriteMatches() {
        when(repository.publish("event-1", CLUB_IDS)).thenReturn(true);

        assertThat(module.publish("event-1", CLUB_IDS)).isEqualTo(EventCommandResult.SUCCESS);
    }

    @Test
    void publishClassifiesAFailureAsNotFoundWhenOutsideTheCallersClubGrants() {
        when(repository.publish("event-1", CLUB_IDS)).thenReturn(false);
        when(repository.existsScoped("event-1", CLUB_IDS)).thenReturn(false);

        assertThat(module.publish("event-1", CLUB_IDS)).isEqualTo(EventCommandResult.NOT_FOUND);
    }

    @Test
    void publishClassifiesAFailureAsNotEditableWhenAlreadyPublished() {
        when(repository.publish("event-1", CLUB_IDS)).thenReturn(false);
        when(repository.existsScoped("event-1", CLUB_IDS)).thenReturn(true);

        assertThat(module.publish("event-1", CLUB_IDS)).isEqualTo(EventCommandResult.NOT_EDITABLE);
    }

    @Test
    void cancelAsOfficerDelegatesToTheRepositoryWithNow() {
        when(repository.cancelAsOfficer("event-1", CLUB_IDS, NOW)).thenReturn(true);

        assertThat(module.cancelAsOfficer("event-1", CLUB_IDS)).isEqualTo(EventCommandResult.SUCCESS);

        InOrder order = inOrder(repository, venueModule);
        order.verify(repository).cancelAsOfficer("event-1", CLUB_IDS, NOW);
        order.verify(venueModule).releaseEventSlots("event-1");
    }

    @Test
    void cancelAsAdminIsUnscopedByClub() {
        when(repository.cancelAsAdmin("event-1", NOW)).thenReturn(true);

        assertThat(module.cancelAsAdmin("event-1")).isEqualTo(EventCommandResult.SUCCESS);
        verify(repository).cancelAsAdmin("event-1", NOW);
    }

    @Test
    void cancelAsAdminClassifiesAFailureAsNotFoundWhenTheEventDoesNotExistAtAll() {
        when(repository.cancelAsAdmin("event-1", NOW)).thenReturn(false);
        when(repository.exists("event-1")).thenReturn(false);

        assertThat(module.cancelAsAdmin("event-1")).isEqualTo(EventCommandResult.NOT_FOUND);
    }

    @Test
    void cancelAsAdminClassifiesAFailureAsNotEditableWhenAlreadyCancelled() {
        when(repository.cancelAsAdmin("event-1", NOW)).thenReturn(false);
        when(repository.exists("event-1")).thenReturn(true);

        assertThat(module.cancelAsAdmin("event-1")).isEqualTo(EventCommandResult.NOT_EDITABLE);
    }

    @Test
    void aFailedOldSlotReleaseHappensOnlyAfterTheNewSlotAndEventMoveHaveSucceeded() {
        Instant oldStart = NOW.plusSeconds(3_600);
        Instant oldEnd = NOW.plusSeconds(7_200);
        Instant newStart = NOW.plusSeconds(10_800);
        Instant newEnd = NOW.plusSeconds(14_400);
        Event event = mock(Event.class);
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getVenueId()).thenReturn("old-venue");
        when(event.getStartsAt()).thenReturn(oldStart);
        when(event.getEndsAt()).thenReturn(oldEnd);
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));
        when(venueModule.requestSlot("new-venue", "event-1", newStart, newEnd))
                .thenReturn(new SlotRequestResult(SlotRequestOutcome.ACQUIRED, List.of()));
        when(repository.moveToSlot(
                        "event-1",
                        CLUB_IDS,
                        "old-venue",
                        oldStart,
                        oldEnd,
                        "new-venue",
                        newStart,
                        newEnd,
                        NOW))
                .thenReturn(true);
        doThrow(new IllegalStateException("injected release failure"))
                .when(venueModule)
                .releaseSlot("old-venue", "event-1", oldStart, oldEnd);

        assertThatThrownBy(() -> module.bookSlotAsOfficer(
                        "event-1", CLUB_IDS, "new-venue", newStart, newEnd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected release failure");

        InOrder order = inOrder(venueModule, repository);
        order.verify(venueModule).requestSlot("new-venue", "event-1", newStart, newEnd);
        order.verify(repository)
                .moveToSlot(
                        "event-1",
                        CLUB_IDS,
                        "old-venue",
                        oldStart,
                        oldEnd,
                        "new-venue",
                        newStart,
                        newEnd,
                        NOW);
        order.verify(venueModule).releaseSlot("old-venue", "event-1", oldStart, oldEnd);
    }

    @Test
    void bookingRefusesMissingCancelledAndStartedEventsBeforeAcquiringAnything() {
        Event cancelled = mock(Event.class);
        when(cancelled.getStatus()).thenReturn(EventStatus.CANCELLED);
        Event started = mock(Event.class);
        when(started.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(started.getStartsAt()).thenReturn(NOW);
        when(repository.findScopedById("missing", CLUB_IDS)).thenReturn(Optional.empty());
        when(repository.findScopedById("cancelled", CLUB_IDS)).thenReturn(Optional.of(cancelled));
        when(repository.findScopedById("started", CLUB_IDS)).thenReturn(Optional.of(started));

        assertThat(module.bookSlotAsOfficer("missing", CLUB_IDS, "venue-1", NOW, NOW.plusSeconds(1)))
                .isEqualTo(SlotCommandOutcome.NOT_FOUND);
        assertThat(module.bookSlotAsOfficer("cancelled", CLUB_IDS, "venue-1", NOW, NOW.plusSeconds(1)))
                .isEqualTo(SlotCommandOutcome.NOT_EDITABLE);
        assertThat(module.bookSlotAsOfficer("started", CLUB_IDS, "venue-1", NOW, NOW.plusSeconds(1)))
                .isEqualTo(SlotCommandOutcome.SLOT_ALREADY_STARTED);
        verify(venueModule, never()).requestSlot(any(), any(), any(), any());
    }

    @Test
    void repeatingTheEventsCurrentSlotIsIdempotentlySuccessful() {
        Instant startsAt = NOW.plusSeconds(3_600);
        Instant endsAt = NOW.plusSeconds(7_200);
        Event event = bookableEvent("venue-1", startsAt);
        when(event.getEndsAt()).thenReturn(endsAt);
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));

        assertThat(module.bookSlotAsOfficer("event-1", CLUB_IDS, "venue-1", startsAt, endsAt))
                .isEqualTo(SlotCommandOutcome.SUCCESS);
        verify(venueModule, never()).requestSlot(any(), any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("venueRequestRefusals")
    void bookingKeepsEveryVenueRequestRefusalStable(
            SlotRequestOutcome venueOutcome,
            SlotCommandOutcome eventOutcome) {
        Instant startsAt = NOW.plusSeconds(3_600);
        Instant endsAt = NOW.plusSeconds(7_200);
        Event event = bookableEvent(null, startsAt);
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));
        when(venueModule.requestSlot("venue-1", "event-1", startsAt, endsAt))
                .thenReturn(new SlotRequestResult(venueOutcome, List.of()));

        assertThat(module.bookSlotAsOfficer("event-1", CLUB_IDS, "venue-1", startsAt, endsAt))
                .isEqualTo(eventOutcome);
        verify(repository, never()).moveToSlot(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("failedMoveClassifications")
    void aLostEventCompareAndSetReleasesTheNewSlot(
            boolean stillExists,
            SlotCommandOutcome expected) {
        Instant oldStart = NOW.plusSeconds(3_600);
        Instant oldEnd = NOW.plusSeconds(7_200);
        Instant newStart = NOW.plusSeconds(10_800);
        Instant newEnd = NOW.plusSeconds(14_400);
        Event event = bookableEvent(null, oldStart);
        when(event.getEndsAt()).thenReturn(oldEnd);
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));
        when(venueModule.requestSlot("venue-1", "event-1", newStart, newEnd))
                .thenReturn(new SlotRequestResult(SlotRequestOutcome.ACQUIRED, List.of()));
        when(repository.moveToSlot(
                        "event-1",
                        CLUB_IDS,
                        null,
                        oldStart,
                        oldEnd,
                        "venue-1",
                        newStart,
                        newEnd,
                        NOW))
                .thenReturn(false);
        when(repository.existsScoped("event-1", CLUB_IDS)).thenReturn(stillExists);

        assertThat(module.bookSlotAsOfficer("event-1", CLUB_IDS, "venue-1", newStart, newEnd))
                .isEqualTo(expected);
        verify(venueModule).releaseSlot("venue-1", "event-1", newStart, newEnd);
    }

    @Test
    void adminBookingAndReleaseClassifyMissingEventsWithoutAClubScopedRead() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(module.bookSlotAsAdmin("missing", "venue-1", NOW, NOW.plusSeconds(1)))
                .isEqualTo(SlotCommandOutcome.NOT_FOUND);
        assertThat(module.releaseSlotAsAdmin("missing"))
                .isEqualTo(SlotCommandOutcome.NOT_FOUND);
        verify(repository, never()).findScopedById(eq("missing"), any());
    }

    @Test
    void releasingIsIdempotentWhenTheUpcomingEventHasNoVenue() {
        Event event = mock(Event.class);
        when(event.getStartsAt()).thenReturn(NOW.plusSeconds(3_600));
        when(event.getVenueId()).thenReturn(null);
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));

        assertThat(module.releaseSlotAsOfficer("event-1", CLUB_IDS))
                .isEqualTo(SlotCommandOutcome.SUCCESS);
        verify(repository, never()).clearVenue(any(), any(), any(), any(), any());
        verify(venueModule).releaseEventSlots("event-1");
    }

    @Test
    void releaseRefusesMissingAndAlreadyStartedEvents() {
        Event started = mock(Event.class);
        when(started.getStartsAt()).thenReturn(NOW);
        when(repository.findScopedById("missing", CLUB_IDS)).thenReturn(Optional.empty());
        when(repository.findScopedById("started", CLUB_IDS)).thenReturn(Optional.of(started));

        assertThat(module.releaseSlotAsOfficer("missing", CLUB_IDS))
                .isEqualTo(SlotCommandOutcome.NOT_FOUND);
        assertThat(module.releaseSlotAsOfficer("started", CLUB_IDS))
                .isEqualTo(SlotCommandOutcome.SLOT_ALREADY_STARTED);
        verify(venueModule, never()).releaseEventSlots(any());
    }

    @ParameterizedTest
    @MethodSource("failedMoveClassifications")
    void aLostReleaseCompareAndSetKeepsTheSlot(
            boolean stillExists,
            SlotCommandOutcome expected) {
        Instant startsAt = NOW.plusSeconds(3_600);
        Event event = mock(Event.class);
        when(event.getStartsAt()).thenReturn(startsAt);
        when(event.getVenueId()).thenReturn("venue-1");
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));
        when(repository.clearVenue("event-1", CLUB_IDS, "venue-1", startsAt, NOW))
                .thenReturn(false);
        when(repository.existsScoped("event-1", CLUB_IDS)).thenReturn(stillExists);

        assertThat(module.releaseSlotAsOfficer("event-1", CLUB_IDS)).isEqualTo(expected);
        verify(venueModule, never()).releaseEventSlots(any());
    }

    @Test
    void anUnknownVenueDayIsEmptyWithoutAnEventLookup() {
        when(venueModule.findDay("missing", java.time.LocalDate.parse("2026-03-20")))
                .thenReturn(Optional.empty());

        assertThat(module.findVenueDay("missing", java.time.LocalDate.parse("2026-03-20")))
                .isEmpty();
        verify(repository, never()).cancelledEventIds(any());
    }

    @Test
    void browseDefaultsToStartsAtAscendingWhenThereIsNoSearchTermAndNoExplicitSort() {
        EventBrowseQuery query = new EventBrowseQuery(null, null, null, null, null, null, null, 0, 20);
        EventPage page = new EventPage(List.of(), 0, 20, 0);
        when(repository.browse(eq(query), eq(EventSort.STARTS_AT_ASC), eq(NOW))).thenReturn(page);

        assertThat(module.browse(query)).isEqualTo(page);
    }

    @Test
    void browseDefaultsToRelevanceWhenASearchTermIsPresentAndNoExplicitSortIsGiven() {
        EventBrowseQuery query = new EventBrowseQuery("robot", null, null, null, null, null, null, 0, 20);
        EventPage page = new EventPage(List.of(), 0, 20, 0);
        when(repository.browse(eq(query), eq(EventSort.RELEVANCE), eq(NOW))).thenReturn(page);

        assertThat(module.browse(query)).isEqualTo(page);
    }

    @Test
    void browseTreatsABlankSearchTermAsNoSearch() {
        EventBrowseQuery query = new EventBrowseQuery(" ", null, null, null, null, null, null, 0, 20);
        EventPage page = new EventPage(List.of(), 0, 20, 0);
        when(repository.browse(eq(query), eq(EventSort.STARTS_AT_ASC), eq(NOW))).thenReturn(page);

        assertThat(module.browse(query)).isEqualTo(page);
    }

    @Test
    void browseHonoursAnExplicitSortEvenWhenSearchingSilentlyDiscardingRelevance() {
        EventBrowseQuery query =
                new EventBrowseQuery("robot", null, null, null, null, null, EventSort.STARTS_AT_DESC, 0, 20);
        EventPage page = new EventPage(List.of(), 0, 20, 0);
        when(repository.browse(eq(query), eq(EventSort.STARTS_AT_DESC), eq(NOW))).thenReturn(page);

        assertThat(module.browse(query)).isEqualTo(page);
    }

    @Test
    void browseClampsAnOversizedPageSizeToTheHundredCap() {
        EventBrowseQuery oversized = new EventBrowseQuery(null, null, null, null, null, null, null, 0, 500);
        EventBrowseQuery clamped = new EventBrowseQuery(null, null, null, null, null, null, null, 0, 100);
        EventPage page = new EventPage(List.of(), 0, 100, 0);
        when(repository.browse(eq(clamped), any(EventSort.class), eq(NOW))).thenReturn(page);

        assertThat(module.browse(oversized)).isEqualTo(page);
    }

    @Test
    void findForStudentReturnsAPublishedEvent() {
        Event event = eventWithStatus(EventStatus.PUBLISHED);
        when(repository.findById("event-1")).thenReturn(Optional.of(event));

        assertThat(module.findForStudent("event-1")).contains(event);
    }

    @Test
    void findForStudentReturnsACancelledEventTooTheFreezeIsNotHidden() {
        Event event = eventWithStatus(EventStatus.CANCELLED);
        when(repository.findById("event-1")).thenReturn(Optional.of(event));

        assertThat(module.findForStudent("event-1")).contains(event);
    }

    @Test
    void findForStudentHidesADraftTheSameWayBrowseDoes() {
        Event event = eventWithStatus(EventStatus.DRAFT);
        when(repository.findById("event-1")).thenReturn(Optional.of(event));

        assertThat(module.findForStudent("event-1")).isEmpty();
    }

    @Test
    void findForStudentIsEmptyWhenNoSuchEventExistsAtAll() {
        when(repository.findById("event-1")).thenReturn(Optional.empty());

        assertThat(module.findForStudent("event-1")).isEmpty();
    }

    @Test
    void registerReturnsSuccessWhenTheGuardedWriteApplies() {
        when(repository.takeSeat("event-1", "student-1", NOW)).thenReturn(true);

        assertThat(module.register("event-1", "student-1")).isEqualTo(RegistrationOutcome.SUCCESS);
    }

    @Test
    void registerJoinsTheWaitlistWhenTakingASeatLoses() {
        when(repository.takeSeat("event-1", "student-1", NOW)).thenReturn(false);
        when(repository.joinWaitlist("event-1", "student-1", NOW)).thenReturn(true);

        assertThat(module.register("event-1", "student-1")).isEqualTo(RegistrationOutcome.SUCCESS);
    }

    @Test
    void registerClassifiesAsNotFoundWhenTheEventDoesNotExistAtAll() {
        when(repository.takeSeat("event-1", "student-1", NOW)).thenReturn(false);
        when(repository.findById("event-1")).thenReturn(Optional.empty());

        assertThat(module.register("event-1", "student-1")).isEqualTo(RegistrationOutcome.NOT_FOUND);
    }

    @Test
    void registerClassifiesAsNotFoundWhenTheEventIsAStillUnpublishedDraft() {
        Event draft = eventWithStatus(EventStatus.DRAFT);
        when(repository.takeSeat("event-1", "student-1", NOW)).thenReturn(false);
        when(repository.findById("event-1")).thenReturn(Optional.of(draft));

        assertThat(module.register("event-1", "student-1")).isEqualTo(RegistrationOutcome.NOT_FOUND);
    }

    @Test
    void registerDelegatesToRegistrationOutcomeClassifyFailureWhenTheEventIsVisible() {
        Event cancelled = eventWithStatus(EventStatus.CANCELLED);
        when(repository.takeSeat("event-1", "student-1", NOW)).thenReturn(false);
        when(repository.findById("event-1")).thenReturn(Optional.of(cancelled));

        // The specific reason for every combination of Status/timestamps/membership is
        // RegistrationOutcomeTest's job; this only proves the module delegates to it rather than
        // inventing its own classification.
        assertThat(module.register("event-1", "student-1")).isEqualTo(RegistrationOutcome.EVENT_CANCELLED);
    }

    @Test
    void requestSeatMakesAStaleFormRevisionRetryBeforeJoiningTheWaitlist() {
        Event changed = eventWithStatus(EventStatus.PUBLISHED);
        when(changed.getRegistrationFormRevision()).thenReturn(2);
        when(repository.takeSeatForForm("event-1", "student-1", NOW, 1))
                .thenReturn(Optional.empty());
        when(repository.findById("event-1")).thenReturn(Optional.of(changed));

        assertThat(module.requestSeat("event-1", "student-1", 1))
                .isEqualTo(new SeatRequestResult(SeatRequestOutcome.FORM_CHANGED, null));
        verify(repository, never()).joinWaitlist("event-1", "student-1", NOW);
    }

    @Test
    void requestSeatWinsAgainstTheExactFormRevisionAlreadyValidated() {
        when(repository.takeSeatForForm("event-1", "student-1", NOW, 3))
                .thenReturn(Optional.of(42L));

        assertThat(module.requestSeat("event-1", "student-1", 3))
                .isEqualTo(new SeatRequestResult(SeatRequestOutcome.SUCCESS, 42L));
    }

    @Test
    void requestSeatHidesAMissingOrDraftEvent() {
        Event draft = eventWithStatus(EventStatus.DRAFT);
        when(repository.findById("missing")).thenReturn(Optional.empty());
        when(repository.findById("draft")).thenReturn(Optional.of(draft));

        assertThat(module.requestSeat("missing", "student-1", 0))
                .isEqualTo(new SeatRequestResult(SeatRequestOutcome.NOT_FOUND, null));
        assertThat(module.requestSeat("draft", "student-1", 0))
                .isEqualTo(new SeatRequestResult(SeatRequestOutcome.NOT_FOUND, null));
    }

    @Test
    void withdrawReturnsSuccessWhenAnEnrolledStudentIsRemoved() {
        when(repository.withdrawEnrolled("event-1", "student-1", NOW)).thenReturn(true);

        assertThat(module.withdraw("event-1", "student-1")).isEqualTo(WithdrawalOutcome.SUCCESS);
    }

    @Test
    void withdrawFallsBackToLeavingTheWaitlist() {
        when(repository.withdrawEnrolled("event-1", "student-1", NOW)).thenReturn(false);
        when(repository.leaveWaitlist("event-1", "student-1", NOW)).thenReturn(true);

        assertThat(module.withdraw("event-1", "student-1")).isEqualTo(WithdrawalOutcome.SUCCESS);
    }

    @Test
    void withdrawRetriesSeatRemovalWhenTheStudentIsPromotedDuringTheRequest() {
        when(repository.withdrawEnrolled("event-1", "student-1", NOW)).thenReturn(false, true);
        when(repository.leaveWaitlist("event-1", "student-1", NOW)).thenReturn(false);

        assertThat(module.withdraw("event-1", "student-1")).isEqualTo(WithdrawalOutcome.SUCCESS);
        verify(repository, times(2)).withdrawEnrolled("event-1", "student-1", NOW);
    }

    @Test
    void withdrawReportsEventCancelledWhenTheFrozenEventIsVisible() {
        Event cancelled = eventWithStatus(EventStatus.CANCELLED);
        when(repository.findById("event-1")).thenReturn(Optional.of(cancelled));

        assertThat(module.withdraw("event-1", "student-1"))
                .isEqualTo(WithdrawalOutcome.EVENT_CANCELLED);
    }

    @Test
    void withdrawReportsEventStartedAtTheExactFreezeInstant() {
        Event started = eventWithStatus(EventStatus.PUBLISHED);
        when(started.getStartsAt()).thenReturn(NOW);
        when(repository.findById("event-1")).thenReturn(Optional.of(started));

        assertThat(module.withdraw("event-1", "student-1"))
                .isEqualTo(WithdrawalOutcome.EVENT_STARTED);
    }

    @Test
    void repeatedWithdrawalBeforeTheEventStartsIsIdempotentlySuccessful() {
        Event upcoming = eventWithStatus(EventStatus.PUBLISHED);
        when(upcoming.getStartsAt()).thenReturn(NOW.plusSeconds(1));
        when(repository.findById("event-1")).thenReturn(Optional.of(upcoming));

        assertThat(module.withdraw("event-1", "student-1")).isEqualTo(WithdrawalOutcome.SUCCESS);
    }

    @Test
    void withdrawHidesAMissingOrDraftEvent() {
        when(repository.findById("missing-event")).thenReturn(Optional.empty());
        Event draft = eventWithStatus(EventStatus.DRAFT);
        when(repository.findById("draft-event")).thenReturn(Optional.of(draft));

        assertThat(module.withdraw("missing-event", "student-1"))
                .isEqualTo(WithdrawalOutcome.NOT_FOUND);
        assertThat(module.withdraw("draft-event", "student-1"))
                .isEqualTo(WithdrawalOutcome.NOT_FOUND);
    }

    @Test
    void findEnrolledDelegatesToTheRepository() {
        EventPage page = new EventPage(List.of(), 0, 20, 0);
        when(repository.findEnrolled("student-1", 0, 20)).thenReturn(page);

        assertThat(module.findEnrolled("student-1", 0, 20)).isEqualTo(page);
    }

    @Test
    void findEnrolledClampsAnOversizedPageSizeToTheHundredCap() {
        EventPage page = new EventPage(List.of(), 0, 100, 0);
        when(repository.findEnrolled("student-1", 0, 100)).thenReturn(page);

        assertThat(module.findEnrolled("student-1", 0, 500)).isEqualTo(page);
    }

    @Test
    void findEnrolledFloorsANegativePageToZero() {
        EventPage page = new EventPage(List.of(), 0, 20, 0);
        when(repository.findEnrolled("student-1", 0, 20)).thenReturn(page);

        assertThat(module.findEnrolled("student-1", -1, 20)).isEqualTo(page);
    }

    private static Stream<Arguments> venueRequestRefusals() {
        return Stream.of(
                Arguments.of(SlotRequestOutcome.NOT_FOUND, SlotCommandOutcome.NOT_FOUND),
                Arguments.of(SlotRequestOutcome.SLOT_TAKEN, SlotCommandOutcome.SLOT_TAKEN),
                Arguments.of(
                        SlotRequestOutcome.SLOT_CROSSES_MIDNIGHT,
                        SlotCommandOutcome.SLOT_CROSSES_MIDNIGHT),
                Arguments.of(
                        SlotRequestOutcome.SLOT_IN_DST_TRANSITION,
                        SlotCommandOutcome.SLOT_IN_DST_TRANSITION),
                Arguments.of(
                        SlotRequestOutcome.SLOT_ALREADY_STARTED,
                        SlotCommandOutcome.SLOT_ALREADY_STARTED));
    }

    private static Stream<Arguments> failedMoveClassifications() {
        return Stream.of(
                Arguments.of(true, SlotCommandOutcome.NOT_EDITABLE),
                Arguments.of(false, SlotCommandOutcome.NOT_FOUND));
    }

    private static Event bookableEvent(String venueId, Instant startsAt) {
        Event event = mock(Event.class);
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getVenueId()).thenReturn(venueId);
        when(event.getStartsAt()).thenReturn(startsAt);
        return event;
    }

    private static Event someEvent() {
        return new Event(
                "club-a", "Title", "Description", NOW, NOW.plusSeconds(10), NOW.plusSeconds(20),
                NOW.plusSeconds(30), 5);
    }

    // Event's rich-state constructor is package-private to event.domain on purpose — production code
    // never builds one outside a guarded MongoTemplate write. A mock stands in for "some Event whose
    // Status is X" here, since Mockito needs no accessible constructor.
    private static Event eventWithStatus(EventStatus status) {
        Event event = mock(Event.class);
        when(event.getStatus()).thenReturn(status);
        return event;
    }
}
