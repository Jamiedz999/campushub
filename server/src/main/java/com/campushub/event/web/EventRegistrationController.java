package com.campushub.event.web;

import com.campushub.event.EventModule;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.RegistrationOutcome;
import com.campushub.event.domain.WithdrawalOutcome;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import com.campushub.shared.PageResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Every signed-in account is a Student — there is no Club or officer scoping here, only "which Student
// is this" from the session. See docs/adr/04-define-registration-capacity-and-waitlist.md and
// docs/adr/15-define-http-api-and-time-contract.md's named-sub-resource rule: taking a Seat is
// POST .../registration, never a verb on the Event itself.
@RestController
class EventRegistrationController {

    private final IdentityAccessModule identityAccessModule;
    private final EventModule eventModule;
    private final Clock clock;

    EventRegistrationController(IdentityAccessModule identityAccessModule, EventModule eventModule, Clock clock) {
        this.identityAccessModule = identityAccessModule;
        this.eventModule = eventModule;
        this.clock = clock;
    }

    @GetMapping("/api/events/{eventId}/registration")
    EventRegistrationView get(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        return currentView(eventId, actor);
    }

    @PostMapping("/api/events/{eventId}/registration")
    EventRegistrationView register(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        handle(eventModule.register(eventId, actor.accountId()));
        return currentView(eventId, actor);
    }

    @DeleteMapping("/api/events/{eventId}/registration")
    EventRegistrationView withdraw(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        handle(eventModule.withdraw(eventId, actor.accountId()));
        return currentView(eventId, actor);
    }

    @GetMapping("/api/events/mine")
    PageResponse<EventRegistrationView> mine(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        CurrentActor actor = identityAccessModule.currentActor();
        EventPage result = eventModule.findEnrolled(actor.accountId(), page, size);
        Instant now = clock.instant();
        return new PageResponse<>(
                result.items().stream()
                        .map(event -> EventRegistrationView.from(event, actor.accountId(), now))
                        .toList(),
                result.page(),
                result.size(),
                result.total());
    }

    private EventRegistrationView currentView(String eventId, CurrentActor actor) {
        Event event = eventModule.findForStudent(eventId).orElseThrow(() -> new NotFoundException("No such Event."));
        return EventRegistrationView.from(event, actor.accountId(), clock.instant());
    }

    private static void handle(RegistrationOutcome outcome) {
        switch (outcome) {
            case SUCCESS -> {
                // Nothing to do — the caller re-reads the current view.
            }
            case NOT_FOUND -> throw new NotFoundException("No such Event.");
            case EVENT_CANCELLED ->
                throw new ConflictException(ErrorCode.EVENT_CANCELLED, "This Event was cancelled.");
            case EVENT_STARTED ->
                throw new ConflictException(ErrorCode.EVENT_STARTED, "This Event has already started.");
            case REGISTRATION_NOT_OPEN ->
                throw new ConflictException(ErrorCode.REGISTRATION_NOT_OPEN, "Registration is not open yet.");
            case REGISTRATION_CLOSED ->
                throw new ConflictException(ErrorCode.REGISTRATION_CLOSED, "Registration has closed.");
            case ALREADY_ENROLLED ->
                throw new ConflictException(ErrorCode.ALREADY_ENROLLED, "You are already enrolled in this Event.");
            case ALREADY_WAITLISTED ->
                throw new ConflictException(
                        ErrorCode.ALREADY_WAITLISTED, "You are already on the Waitlist for this Event.");
            case EVENT_FULL -> throw new ConflictException(ErrorCode.EVENT_FULL, "This Event is full.");
        }
    }

    private static void handle(WithdrawalOutcome outcome) {
        switch (outcome) {
            case SUCCESS -> {
                // Nothing to do — the caller re-reads the current view.
            }
            case NOT_FOUND -> throw new NotFoundException("No such Event.");
            case EVENT_CANCELLED ->
                throw new ConflictException(ErrorCode.EVENT_CANCELLED, "This Event was cancelled.");
            case EVENT_STARTED ->
                throw new ConflictException(ErrorCode.EVENT_STARTED, "This Event has already started.");
        }
    }
}
