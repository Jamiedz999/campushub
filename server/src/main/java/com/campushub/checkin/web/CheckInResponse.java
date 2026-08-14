package com.campushub.checkin.web;

import com.campushub.checkin.CheckInModule.ScanResult;
import com.campushub.event.EventModule.AttendanceMethod;
import java.time.Instant;

// The one success shape. Every refusal is problem+json carrying a `code` instead, because the door's
// five failure states each need their own wording — see docs/adr/15-define-http-api-and-time-contract.md.
record CheckInResponse(String eventId, String eventTitle, Instant at, AttendanceMethod method) {

    static CheckInResponse from(String eventId, ScanResult result) {
        return new CheckInResponse(eventId, result.eventTitle(), result.at(), result.method());
    }
}
