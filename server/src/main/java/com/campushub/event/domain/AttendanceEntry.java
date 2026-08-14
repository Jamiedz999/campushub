package com.campushub.event.domain;

import com.campushub.event.EventModule.AttendanceMethod;
import java.time.Instant;

// One Student marked present, appended to the Event document by the guarded attendance write — see
// docs/adr/07-define-qr-checkin-and-anti-fraud.md. `method` is what keeps a scan and an officer's
// override from ever looking the same; a club whose attendance is mostly MANUAL has not demonstrated
// attendance, and the dashboard is meant to say so. Lateness is NOT stored: a record whose `at` is
// after startsAt is derived as late on read, the same reason Phase is never written down.
public record AttendanceEntry(String studentId, Instant at, AttendanceMethod method) {}
