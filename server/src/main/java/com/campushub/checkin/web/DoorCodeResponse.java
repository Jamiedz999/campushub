package com.campushub.checkin.web;

import com.campushub.checkin.CheckInModule.DoorCode;
import java.time.Instant;

// The Officer's door screen. Counts, never Student ids: the manual-override list is a separate,
// separately authorized read (GET /api/events/{eventId}/attendance).
record DoorCodeResponse(
        String eventId,
        String title,
        String token,
        Instant rotatesAt,
        Instant checkInOpensAt,
        Instant checkInClosesAt,
        boolean checkInOpen,
        int capacity,
        int enrolledCount,
        int attendedCount) {

    static DoorCodeResponse from(DoorCode doorCode) {
        return new DoorCodeResponse(
                doorCode.eventId(),
                doorCode.title(),
                doorCode.token(),
                doorCode.rotatesAt(),
                doorCode.checkInOpensAt(),
                doorCode.checkInClosesAt(),
                doorCode.checkInOpen(),
                doorCode.capacity(),
                doorCode.enrolledCount(),
                doorCode.attendedCount());
    }
}
