package com.campushub.event.domain;

// How a Seat was won. DIRECT is the only value this Issue ever writes; PROMOTED is written by the
// Waitlist promotion write — see docs/adr/04-define-registration-capacity-and-waitlist.md — but the
// value already belongs to the Seat Ledger's stored shape, not to whichever Issue happens to produce it.
public enum EnrollmentVia {
    DIRECT,
    PROMOTED
}
