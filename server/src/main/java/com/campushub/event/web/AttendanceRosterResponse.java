package com.campushub.event.web;

import com.campushub.event.EventModule.AttendanceMethod;
import com.campushub.event.EventModule.AttendanceRoster;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// The Officer's door list: who holds a Seat, and whether they are in the room yet. Scanned and manual
// records are never blurred together — see docs/adr/07-define-qr-checkin-and-anti-fraud.md — so the
// method travels with each row rather than being flattened into "present".
record AttendanceRosterResponse(
        String eventId,
        String title,
        int capacity,
        int enrolledCount,
        int attendedCount,
        List<AttendeeResponse> items) {

    record AttendeeResponse(String studentId, String displayName, Instant attendedAt, AttendanceMethod method) {}

    static AttendanceRosterResponse from(AttendanceRoster roster, Map<String, String> displayNames) {
        return new AttendanceRosterResponse(
                roster.eventId(),
                roster.title(),
                roster.capacity(),
                roster.enrolledCount(),
                roster.attendedCount(),
                roster.items().stream()
                        .map(entry -> new AttendeeResponse(
                                entry.studentId(),
                                displayNames.getOrDefault(entry.studentId(), entry.studentId()),
                                entry.attendedAt(),
                                entry.method()))
                        .toList());
    }
}
