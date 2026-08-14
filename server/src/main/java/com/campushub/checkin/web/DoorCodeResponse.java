package com.campushub.checkin.web;

import com.campushub.checkin.CheckInModule.DoorCode;
import java.time.Instant;

// The Officer's door screen: the code of the moment and the window it will be judged against. How many
// people are in the room comes from the Roster read beside it (GET /api/events/{eventId}/attendance),
// which is separately authorized and is the read the manual override already needs.
record DoorCodeResponse(
        String eventId,
        String title,
        String token,
        Instant rotatesAt,
        Instant checkInOpensAt,
        Instant checkInClosesAt,
        boolean checkInOpen) {

    static DoorCodeResponse from(DoorCode doorCode) {
        return new DoorCodeResponse(
                doorCode.eventId(),
                doorCode.title(),
                doorCode.token(),
                doorCode.rotatesAt(),
                doorCode.checkInOpensAt(),
                doorCode.checkInClosesAt(),
                doorCode.checkInOpen());
    }
}
