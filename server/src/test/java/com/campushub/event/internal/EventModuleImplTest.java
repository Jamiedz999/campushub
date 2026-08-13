package com.campushub.event.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventSort;
import com.campushub.event.domain.EventStatus;
import com.campushub.event.domain.RegistrationOutcome;
import com.campushub.event.domain.WithdrawalOutcome;
import com.campushub.event.persistence.EventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventModuleImplTest {

    private static final Instant NOW = Instant.parse("2026-03-05T00:00:00Z");
    private static final Set<String> CLUB_IDS = Set.of("club-a");

    @Mock
    private EventRepository repository;

    private EventModuleImpl module;

    @BeforeEach
    void setUp() {
        module = new EventModuleImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
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
