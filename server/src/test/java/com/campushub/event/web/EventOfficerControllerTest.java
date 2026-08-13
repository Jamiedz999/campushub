package com.campushub.event.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.event.EventModule;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.shared.EventNotEditableException;
import com.campushub.shared.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventOfficerControllerTest {

    private static final Instant NOW = Instant.parse("2026-03-05T00:00:00Z");

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private EventModule eventModule;

    private EventOfficerController controller;

    @BeforeEach
    void setUp() {
        controller = new EventOfficerController(identityAccessModule, eventModule, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void anOfficerCanCreateADraftInTheirOwnClub() {
        actingAs(officer("club-a"));
        when(eventModule.createDraft(
                        "club-a", "Title", "Description", NOW, NOW.plusSeconds(10), NOW.plusSeconds(20),
                        NOW.plusSeconds(30), 5))
                .thenReturn("event-1");
        when(eventModule.findForOfficer("event-1", Set.of("club-a"))).thenReturn(Optional.of(someEvent()));

        EventOfficerResponse response = controller.create("club-a", createRequest());

        assertThat(response.title()).isEqualTo("Title");
    }

    @Test
    void aUniversityAdminCannotCreateAnEvent() {
        actingAs(admin());

        assertThatThrownBy(() -> controller.create("club-a", createRequest()))
                .isInstanceOf(NotFoundException.class);
        verify(eventModule, never()).createDraft(any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void anOfficerCannotCreateAnEventInAnotherClub() {
        actingAs(officer("club-b"));

        assertThatThrownBy(() -> controller.create("club-a", createRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getReturnsTheEventWhenScopedToTheCallersClub() {
        actingAs(officer("club-a"));
        when(eventModule.findForOfficer("event-1", Set.of("club-a"))).thenReturn(Optional.of(someEvent()));

        EventOfficerResponse response = controller.get("event-1");

        assertThat(response.title()).isEqualTo("Title");
    }

    @Test
    void getThrowsNotFoundWhenTheEventIsOutsideTheCallersClubGrants() {
        actingAs(officer("club-b"));
        when(eventModule.findForOfficer("event-1", Set.of("club-b"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get("event-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void editReturnsTheUpdatedViewOnSuccess() {
        actingAs(officer("club-a"));
        EditEventRequest request = new EditEventRequest("New title", null, null, null, null, null, null);
        when(eventModule.edit("event-1", Set.of("club-a"), request.toEventEdit()))
                .thenReturn(EventCommandResult.SUCCESS);
        when(eventModule.findForOfficer("event-1", Set.of("club-a"))).thenReturn(Optional.of(someEvent()));

        EventOfficerResponse response = controller.edit("event-1", request);

        assertThat(response.title()).isEqualTo("Title");
    }

    @Test
    void editThrowsNotFoundWhenTheGuardReportsNotFound() {
        actingAs(officer("club-b"));
        EditEventRequest request = new EditEventRequest("New title", null, null, null, null, null, null);
        when(eventModule.edit("event-1", Set.of("club-b"), request.toEventEdit()))
                .thenReturn(EventCommandResult.NOT_FOUND);

        assertThatThrownBy(() -> controller.edit("event-1", request)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void editThrowsEventNotEditableWhenTheLifecycleGuardRefuses() {
        actingAs(officer("club-a"));
        EditEventRequest request = new EditEventRequest(null, null, null, null, null, null, 1);
        when(eventModule.edit("event-1", Set.of("club-a"), request.toEventEdit()))
                .thenReturn(EventCommandResult.NOT_EDITABLE);

        assertThatThrownBy(() -> controller.edit("event-1", request)).isInstanceOf(EventNotEditableException.class);
    }

    @Test
    void publishReturnsTheUpdatedViewOnSuccess() {
        actingAs(officer("club-a"));
        when(eventModule.publish("event-1", Set.of("club-a"))).thenReturn(EventCommandResult.SUCCESS);
        when(eventModule.findForOfficer("event-1", Set.of("club-a"))).thenReturn(Optional.of(someEvent()));

        EventOfficerResponse response = controller.publish("event-1");

        assertThat(response.title()).isEqualTo("Title");
    }

    @Test
    void publishingAnAlreadyPublishedEventIsNotEditable() {
        actingAs(officer("club-a"));
        when(eventModule.publish("event-1", Set.of("club-a"))).thenReturn(EventCommandResult.NOT_EDITABLE);

        assertThatThrownBy(() -> controller.publish("event-1")).isInstanceOf(EventNotEditableException.class);
    }

    @Test
    void anOfficerCancelsThroughTheScopedPath() {
        actingAs(officer("club-a"));
        when(eventModule.cancelAsOfficer("event-1", Set.of("club-a"))).thenReturn(EventCommandResult.SUCCESS);

        controller.cancel("event-1");

        verify(eventModule).cancelAsOfficer("event-1", Set.of("club-a"));
        verify(eventModule, never()).cancelAsAdmin(any());
    }

    @Test
    void anOfficerCancellingAnotherClubsEventIsNotFound() {
        actingAs(officer("club-b"));
        when(eventModule.cancelAsOfficer("event-1", Set.of("club-b"))).thenReturn(EventCommandResult.NOT_FOUND);

        assertThatThrownBy(() -> controller.cancel("event-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aUniversityAdminCancelsThroughTheUnscopedPathRegardlessOfClub() {
        actingAs(admin());
        when(eventModule.cancelAsAdmin("event-1")).thenReturn(EventCommandResult.SUCCESS);

        controller.cancel("event-1");

        verify(eventModule).cancelAsAdmin("event-1");
        verify(eventModule, never()).cancelAsOfficer(any(), any());
    }

    private void actingAs(CurrentActor actor) {
        when(identityAccessModule.currentActor()).thenReturn(actor);
    }

    private static CurrentActor officer(String clubId) {
        return new CurrentActor("account-1", "officer@demo.campushub", "Officer", SystemRole.STUDENT, Set.of(clubId));
    }

    private static CurrentActor admin() {
        return new CurrentActor("admin-1", "admin@demo.campushub", "Admin", SystemRole.UNIVERSITY_ADMIN, Set.of());
    }

    private static Event someEvent() {
        return new Event(
                "club-a", "Title", "Description", NOW, NOW.plusSeconds(10), NOW.plusSeconds(20),
                NOW.plusSeconds(30), 5);
    }

    private static CreateEventRequest createRequest() {
        return new CreateEventRequest(
                "Title", "Description", NOW, NOW.plusSeconds(10), NOW.plusSeconds(20), NOW.plusSeconds(30), 5);
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
