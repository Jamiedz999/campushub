package com.campushub.event.domain;

import java.time.Instant;

// One held Seat, per docs/adr/04-define-registration-capacity-and-waitlist.md: which Student, how they
// got it (DIRECT or PROMOTED), and when. `via` and `at` exist so a promoted Student can be told they're
// in and an Officer can see which registrations came off the Waitlist, at no extra write.
public record EnrolledEntry(String studentId, EnrollmentVia via, Instant at) {}
