package com.campushub.checkin;

import com.campushub.event.EventModule.AttendanceMethod;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Derives and verifies the rotating door code, and nothing else. See
 * docs/adr/07-define-qr-checkin-and-anti-fraud.md.
 *
 * <p>The design splits the two things a check-in must prove and gives each to whichever party can
 * actually prove it: <b>the rotating code proves presence</b>, because it is worthless a minute later,
 * so possessing it means having seen a screen that is in the room; <b>the authenticated session proves
 * identity</b>, because the code says nothing about who scanned it. Neither half alone admits anyone.
 *
 * <p><b>This module never writes the Seat Ledger.</b> It verifies a scanned code and hands the
 * verified {@code (eventId, studentId)} pair to {@code event}, which owns the Event document and
 * performs the one guarded attendance write. That seam is the point: presence proof and Seat Ledger
 * ownership are different responsibilities.
 */
public interface CheckInModule {

    /**
     * The code the Officer's door screen displays, and the window it will be judged against. The whole
     * room scans the same code in the same window.
     *
     * <p>It carries no attendance count, and still does not now that the count is live. How many
     * people are in the room is a Seat Ledger reading that arrives with the Roster the manual override
     * already reads; the socket only says when to read it again. See {@code com.campushub.realtime}.
     */
    record DoorCode(
            String eventId,
            String title,
            String token,
            Instant rotatesAt,
            Instant checkInOpensAt,
            Instant checkInClosesAt,
            boolean checkInOpen) {}

    /**
     * Every way a scan can end. TOKEN_INVALID is a signature this server did not produce — the only
     * genuinely suspicious one. TOKEN_EXPIRED is the ordinary case of a code that rotated mid-scan, and
     * is worded to the Student as a normal retry rather than as an error.
     */
    enum ScanOutcome {
        SUCCESS,
        TOKEN_INVALID,
        TOKEN_EXPIRED,
        NOT_ON_ROSTER,
        ALREADY_CHECKED_IN,
        CHECK_IN_WINDOW_CLOSED,
        NOT_FOUND
    }

    /** {@code at} and {@code method} describe the attendance record on SUCCESS and ALREADY_CHECKED_IN. */
    record ScanResult(ScanOutcome outcome, String eventTitle, Instant at, AttendanceMethod method) {

        public static ScanResult refused(ScanOutcome outcome) {
            return new ScanResult(outcome, null, null, null);
        }
    }

    /** The code for this instant, scoped to the caller's officer Clubs. Empty means "not entitled". */
    Optional<DoorCode> issueDoorCode(String eventId, Set<String> callerOfficerClubIds);

    /**
     * Verifies a scanned code and, only if it verifies, asks {@code event} to record the attendance.
     * {@code eventId} is the Event the Student scanned into; a code signed for a different Event is
     * rejected as TOKEN_INVALID rather than silently checking them in somewhere else.
     */
    ScanResult checkIn(String eventId, String token, String studentId);
}
