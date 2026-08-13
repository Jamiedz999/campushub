package com.campushub.event.web;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.FormUpdateOutcome;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.shared.EventNotEditableException;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Every method here is scoped by the caller's officer Club grants, resolved once from CurrentActor and
// passed into EventModule — never load-then-check. See
// docs/adr/08-define-roles-and-resource-authorization.md. Create uses isOfficerOf rather than
// isEntitledToClub because a University Admin may cancel any Event but may not create, edit or publish
// one — see the permission matrix in that same ADR.
@RestController
class EventOfficerController {

    private final IdentityAccessModule identityAccessModule;
    private final EventModule eventModule;
    private final Clock clock;

    EventOfficerController(IdentityAccessModule identityAccessModule, EventModule eventModule, Clock clock) {
        this.identityAccessModule = identityAccessModule;
        this.eventModule = eventModule;
        this.clock = clock;
    }

    @PostMapping("/api/clubs/{clubId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    EventOfficerResponse create(@PathVariable String clubId, @Valid @RequestBody CreateEventRequest request) {
        CurrentActor actor = identityAccessModule.currentActor();
        if (!actor.isOfficerOf(clubId)) {
            throw new NotFoundException("No such club, or the caller may not create Events in it.");
        }
        String eventId = eventModule.createDraft(
                clubId,
                request.title(),
                request.description(),
                request.registrationOpensAt(),
                request.registrationClosesAt(),
                request.startsAt(),
                request.endsAt(),
                request.capacity());
        return currentView(eventId, actor);
    }

    @GetMapping("/api/events/{eventId}")
    EventOfficerResponse get(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        return currentView(eventId, actor);
    }

    @PatchMapping("/api/events/{eventId}")
    EventOfficerResponse edit(@PathVariable String eventId, @Valid @RequestBody EditEventRequest request) {
        CurrentActor actor = identityAccessModule.currentActor();
        handle(eventModule.edit(eventId, actor.officerClubIds(), request.toEventEdit()));
        return currentView(eventId, actor);
    }

    @PutMapping("/api/events/{eventId}/registration-form")
    EventOfficerResponse updateRegistrationForm(
            @PathVariable String eventId, @Valid @RequestBody UpdateRegistrationFormRequest request) {
        CurrentActor actor = identityAccessModule.currentActor();
        handle(eventModule.updateRegistrationForm(
                eventId, actor.officerClubIds(), request.toRegistrationForm()));
        return currentView(eventId, actor);
    }

    @PostMapping("/api/events/{eventId}/publication")
    EventOfficerResponse publish(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        handle(eventModule.publish(eventId, actor.officerClubIds()));
        return currentView(eventId, actor);
    }

    @PostMapping("/api/events/{eventId}/cancellation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        EventCommandResult result = actor.isUniversityAdmin()
                ? eventModule.cancelAsAdmin(eventId)
                : eventModule.cancelAsOfficer(eventId, actor.officerClubIds());
        handle(result);
    }

    private EventOfficerResponse currentView(String eventId, CurrentActor actor) {
        Event event = eventModule
                .findForOfficer(eventId, actor.officerClubIds())
                .orElseThrow(() -> new NotFoundException("No such Event, or the caller is not its Club's Officer."));
        return EventOfficerResponse.from(event, clock.instant());
    }

    private static void handle(EventCommandResult result) {
        switch (result) {
            case SUCCESS -> {
                // Nothing to do — the caller re-reads the current view.
            }
            case NOT_FOUND ->
                throw new NotFoundException("No such Event, or the caller is not entitled to it.");
            case NOT_EDITABLE ->
                throw new EventNotEditableException(
                        "The Event's current lifecycle state does not allow this change.");
        }
    }

    private static void handle(FormUpdateOutcome result) {
        switch (result) {
            case SUCCESS -> {
                // Nothing to do — the caller re-reads the current view.
            }
            case NOT_FOUND ->
                throw new NotFoundException("No such Event, or the caller is not entitled to it.");
            case NOT_EDITABLE ->
                throw new EventNotEditableException(
                        "The Event's current lifecycle state does not allow this change.");
            case FORM_LOCKED ->
                throw new ConflictException(
                        ErrorCode.FORM_LOCKED,
                        "The registration form is locked because a Student has already registered.");
        }
    }
}
