package com.campushub.event.web;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.SlotCommandOutcome;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.EventNotEditableException;
import com.campushub.shared.NotFoundException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VenueSlotController {

    private final IdentityAccessModule identityAccessModule;
    private final EventModule eventModule;

    VenueSlotController(IdentityAccessModule identityAccessModule, EventModule eventModule) {
        this.identityAccessModule = identityAccessModule;
        this.eventModule = eventModule;
    }

    @PutMapping("/api/events/{eventId}/slot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void book(@PathVariable String eventId, @Valid @RequestBody BookSlotRequest request) {
        CurrentActor actor = identityAccessModule.currentActor();
        SlotCommandOutcome outcome = eventModule.bookSlotAsOfficer(
                eventId,
                actor.officerClubIds(),
                request.venueId(),
                request.startsAt(),
                request.endsAt());
        handle(outcome);
    }

    @DeleteMapping("/api/events/{eventId}/slot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        SlotCommandOutcome outcome = eventModule.releaseSlotAsOfficer(eventId, actor.officerClubIds());
        handle(outcome);
    }

    @GetMapping("/api/venues/{venueId}/days/{date}")
    VenueDayResponse day(@PathVariable String venueId, @PathVariable LocalDate date) {
        CurrentActor actor = identityAccessModule.currentActor();
        if (!actor.isUniversityAdmin() && actor.officerClubIds().isEmpty()) {
            throw new NotFoundException("No such Venue-day timeline.");
        }
        return eventModule
                .findVenueDay(venueId, date)
                .map(VenueDayResponse::from)
                .orElseThrow(() -> new NotFoundException("No such Venue."));
    }

    private static void handle(SlotCommandOutcome outcome) {
        switch (outcome) {
            case SUCCESS -> {
                // The client invalidates and re-reads the Event and Venue-day views.
            }
            case NOT_FOUND -> throw new NotFoundException("No such Event or Venue, or the caller is not entitled.");
            case NOT_EDITABLE -> throw new EventNotEditableException(
                    "The Event's current lifecycle state does not allow this Slot change.");
            case SLOT_TAKEN -> throw conflict(
                    ErrorCode.SLOT_TAKEN, "Another Event already holds an overlapping Slot.");
            case SLOT_CROSSES_MIDNIGHT -> throw conflict(
                    ErrorCode.SLOT_CROSSES_MIDNIGHT, "A Slot may not cross campus midnight.");
            case SLOT_IN_DST_TRANSITION -> throw conflict(
                    ErrorCode.SLOT_IN_DST_TRANSITION,
                    "A Slot may not intersect the daylight-saving transition hour.");
            case SLOT_ALREADY_STARTED -> throw conflict(
                    ErrorCode.SLOT_ALREADY_STARTED, "A Slot may not be changed after the Event starts.");
        }
    }

    private static ConflictException conflict(ErrorCode code, String detail) {
        return new ConflictException(code, detail);
    }
}
