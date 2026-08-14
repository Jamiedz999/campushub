package com.campushub.event.web;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.AttendanceResult;
import com.campushub.event.EventModule.AttendanceRoster;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// The Officer half of attendance: reading the door list, and the manual override for a phone that
// failed, a code that would not read, or a screen that is down. Both are scoped by the caller's Club
// grants inside the module, never load-then-check.
//
// The scan itself is not here. It arrives through checkin, which proves presence and identity before
// this module is asked to write anything — see docs/adr/07-define-qr-checkin-and-anti-fraud.md.
@RestController
class AttendanceController {

    private final IdentityAccessModule identityAccessModule;
    private final EventModule eventModule;

    AttendanceController(IdentityAccessModule identityAccessModule, EventModule eventModule) {
        this.identityAccessModule = identityAccessModule;
        this.eventModule = eventModule;
    }

    @GetMapping("/api/events/{eventId}/attendance")
    AttendanceRosterResponse roster(@PathVariable String eventId) {
        CurrentActor actor = identityAccessModule.currentActor();
        AttendanceRoster roster = eventModule
                .findAttendanceForOfficer(eventId, actor.officerClubIds())
                .orElseThrow(() -> new NotFoundException("No such Event, or the caller is not its Club's Officer."));
        Set<String> studentIds =
                roster.items().stream().map(entry -> entry.studentId()).collect(Collectors.toSet());
        return AttendanceRosterResponse.from(roster, identityAccessModule.displayNames(studentIds));
    }

    @PutMapping("/api/events/{eventId}/attendance/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markPresent(@PathVariable String eventId, @PathVariable String studentId) {
        CurrentActor actor = identityAccessModule.currentActor();
        AttendanceResult result =
                eventModule.recordManualAttendance(eventId, studentId, actor.officerClubIds());
        handle(result);
    }

    private static void handle(AttendanceResult result) {
        switch (result.outcome()) {
            // PUT is idempotent: a Student who is already present is the desired state, whether they got
            // there by scanning or by an earlier override. The $ne guard means no second record exists
            // and a scan was never overwritten by an override.
            case SUCCESS, ALREADY_CHECKED_IN -> {
                // The client re-reads the roster.
            }
            case NOT_FOUND ->
                throw new NotFoundException("No such Event, or the caller is not its Club's Officer.");
            case NOT_ON_ROSTER ->
                throw new ConflictException(
                        ErrorCode.NOT_ON_ROSTER,
                        "That Student was not holding a Seat when the Event started, so there is nothing to mark.");
            case CHECK_IN_WINDOW_CLOSED ->
                throw new ConflictException(
                        ErrorCode.CHECK_IN_WINDOW_CLOSED, "Check-in is not open for this Event.");
        }
    }
}
