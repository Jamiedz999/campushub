package com.campushub.event.web;

import com.campushub.event.EventModule.AttendanceMethod;
import com.campushub.event.EventModule.AttendanceRoster;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// The Officer's door list: who holds a Seat, and whether they are in the room yet.
//
// "Roster" and "enrolled" are the glossary's words for this; CONTEXT.md lists "attendee" and "attendee
// list" under Avoid for exactly the reason ADR 15 binds payload names to the glossary — the Roster says
// who may attend, not who did, and one field here says which. `at` is the same name the stored
// attendance entry uses, so one concept keeps one name from the document to the wire.
//
// Scanned and manual records are never blurred together — see
// docs/adr/07-define-qr-checkin-and-anti-fraud.md — so the method travels with each row rather than
// being flattened into "present", and the counts are left to the reader to derive from the rows.
record AttendanceRosterResponse(String eventId, String title, List<RosterEntryResponse> items) {

    record RosterEntryResponse(String studentId, String displayName, Instant at, AttendanceMethod method) {}

    static AttendanceRosterResponse from(AttendanceRoster roster, Map<String, String> displayNames) {
        return new AttendanceRosterResponse(
                roster.eventId(),
                roster.title(),
                roster.items().stream()
                        .map(entry -> new RosterEntryResponse(
                                entry.studentId(),
                                displayNames.getOrDefault(entry.studentId(), entry.studentId()),
                                entry.at(),
                                entry.method()))
                        .toList());
    }
}
