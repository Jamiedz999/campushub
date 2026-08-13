package com.campushub.event.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventSort;
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

    private static Event someEvent() {
        return new Event(
                "club-a", "Title", "Description", NOW, NOW.plusSeconds(10), NOW.plusSeconds(20),
                NOW.plusSeconds(30), 5);
    }
}
