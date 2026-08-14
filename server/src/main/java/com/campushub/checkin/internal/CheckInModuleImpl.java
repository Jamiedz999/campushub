package com.campushub.checkin.internal;

import com.campushub.checkin.CheckInModule;
import com.campushub.checkin.internal.CheckInTokenCodec.TokenStatus;
import com.campushub.checkin.internal.CheckInTokenCodec.Verification;
import com.campushub.event.EventModule;
import com.campushub.event.EventModule.AttendanceResult;
import com.campushub.event.EventModule.DoorEvent;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class CheckInModuleImpl implements CheckInModule {

    private final CheckInTokenCodec codec;
    private final EventModule eventModule;
    private final Clock clock;

    CheckInModuleImpl(CheckInTokenCodec codec, EventModule eventModule, Clock clock) {
        this.codec = codec;
        this.eventModule = eventModule;
        this.clock = clock;
    }

    @Override
    public Optional<DoorCode> issueDoorCode(String eventId, Set<String> callerOfficerClubIds) {
        // The code is derived even outside the check-in window: it is worthless until the window opens,
        // because the attendance write refuses it, and an Officer setting up the room early should see
        // the screen they are about to project rather than an empty one.
        return eventModule.findDoorEventForOfficer(eventId, callerOfficerClubIds).map(this::doorCodeFor);
    }

    private DoorCode doorCodeFor(DoorEvent event) {
        return new DoorCode(
                event.id(),
                event.title(),
                codec.issue(event.id(), clock.instant()),
                codec.rotatesAt(clock.instant()),
                event.checkInOpensAt(),
                event.checkInClosesAt(),
                event.checkInOpen(),
                event.capacity(),
                event.enrolledCount(),
                event.attendedCount());
    }

    @Override
    public ScanResult checkIn(String eventId, String token, String studentId) {
        Verification verification = codec.verify(token, clock.instant());
        if (verification.status() == TokenStatus.EXPIRED) {
            return ScanResult.refused(ScanOutcome.TOKEN_EXPIRED);
        }
        // A code signed for another Event verifies, and still must not check anyone in here: presence is
        // proven for the room that screen is in, not for whichever Event the scanner happened to open.
        if (verification.status() != TokenStatus.VALID || !eventId.equals(verification.eventId())) {
            return ScanResult.refused(ScanOutcome.TOKEN_INVALID);
        }
        return map(eventModule.recordScannedAttendance(eventId, studentId));
    }

    private static ScanResult map(AttendanceResult result) {
        ScanOutcome outcome = switch (result.outcome()) {
            case SUCCESS -> ScanOutcome.SUCCESS;
            case NOT_FOUND -> ScanOutcome.NOT_FOUND;
            case NOT_ON_ROSTER -> ScanOutcome.NOT_ON_ROSTER;
            case ALREADY_CHECKED_IN -> ScanOutcome.ALREADY_CHECKED_IN;
            case CHECK_IN_WINDOW_CLOSED -> ScanOutcome.CHECK_IN_WINDOW_CLOSED;
        };
        return new ScanResult(outcome, result.eventTitle(), result.at(), result.method());
    }
}
