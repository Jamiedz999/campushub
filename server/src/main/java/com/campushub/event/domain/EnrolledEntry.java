package com.campushub.event.domain;

import java.time.Instant;

// One held Seat, per docs/adr/04-define-registration-capacity-and-waitlist.md: which Student, how they
// got it (DIRECT or PROMOTED), when, and which exact period of enrollment it is. `enrollmentVersion` lets
// the Registration module distinguish current answers from a stale document left by an earlier Seat.
public record EnrolledEntry(
        String studentId, EnrollmentVia via, Instant at, Long enrollmentVersion) {

    /** Reads legacy Event documents created before answer writes needed a Seat-incarnation key. */
    public EnrolledEntry(String studentId, EnrollmentVia via, Instant at) {
        this(studentId, via, at, null);
    }
}
