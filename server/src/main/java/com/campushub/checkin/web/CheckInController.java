package com.campushub.checkin.web;

import com.campushub.checkin.CheckInModule;
import com.campushub.checkin.CheckInModule.ScanResult;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// The door, from both sides. The Officer's screen is scoped by their Club grants, so an Officer of
// another Club asking for a code genuinely finds nothing (404, never 403 — see
// docs/adr/08-define-roles-and-resource-authorization.md). The Student's scan carries no identity of
// its own: who is checking in comes from the session, which is the half of the proof the code cannot
// give.
@RestController
class CheckInController {

    private final IdentityAccessModule identityAccessModule;
    private final CheckInModule checkInModule;

    CheckInController(IdentityAccessModule identityAccessModule, CheckInModule checkInModule) {
        this.identityAccessModule = identityAccessModule;
        this.checkInModule = checkInModule;
    }

    @GetMapping("/api/events/{eventId}/door-code")
    DoorCodeResponse doorCode(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        return checkInModule
                .issueDoorCode(eventId, actor.officerClubIds())
                .map(DoorCodeResponse::from)
                .orElseThrow(() -> new NotFoundException("No such Event, or the caller is not its Club's Officer."));
    }

    @PostMapping("/api/events/{eventId}/attendance")
    CheckInResponse checkIn(@PathVariable String eventId, @Valid @RequestBody ScanRequest request) {
        CurrentActor actor = identityAccessModule.currentActor();
        ScanResult result = checkInModule.checkIn(eventId, request.token(), actor.accountId());
        return handle(eventId, result);
    }

    private static CheckInResponse handle(String eventId, ScanResult result) {
        return switch (result.outcome()) {
            case SUCCESS -> CheckInResponse.from(eventId, result);
            case NOT_FOUND -> throw new NotFoundException("No such Event.");
            case TOKEN_INVALID -> throw new ConflictException(
                    ErrorCode.TOKEN_INVALID, "That code was not issued by this door.");
            case TOKEN_EXPIRED -> throw new ConflictException(
                    ErrorCode.TOKEN_EXPIRED, "That code has rotated. Scan the screen again.");
            case NOT_ON_ROSTER -> throw new ConflictException(
                    ErrorCode.NOT_ON_ROSTER,
                    "You were not holding a Seat when this Event started, so there is no Seat to check into.");
            case CHECK_IN_WINDOW_CLOSED -> throw new ConflictException(
                    ErrorCode.CHECK_IN_WINDOW_CLOSED, "Check-in is not open for this Event.");
            // Idempotent, and reported as reassurance rather than as an error: no second record was
            // created, and the client is told when the first one was so it can say so.
            case ALREADY_CHECKED_IN -> throw alreadyCheckedIn(result);
        };
    }

    private static ConflictException alreadyCheckedIn(ScanResult result) {
        return new ConflictException(
                ErrorCode.ALREADY_CHECKED_IN,
                "You are already checked in to this Event.",
                Map.of("at", result.at(), "method", result.method()));
    }
}
