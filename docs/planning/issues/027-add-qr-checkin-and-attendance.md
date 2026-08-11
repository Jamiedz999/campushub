# CH-027 · Add QR check-in and attendance

Sprint: 4
Area: checkin, event
Blocked by: 024, 026
Decisions: [QR check-in](../../adr/07-define-qr-checkin-and-anti-fraud.md)

## Change

- `checkin` module owning `CheckInTokenCodec`: HMAC over `(eventId, windowIndex)` with a server secret, 60-second windows, verification accepting the current window and the previous one. Nothing is stored.
- `checkin` verifies and hands a verified `(eventId, studentId)` to `event`, which performs the attendance write. **`checkin` never writes the Seat Ledger.**
- Attendance appends `{ studentId, at, method }` to the Event document in one guarded write: enrolled-only Roster check, `$ne` idempotency guard.
- Check-in window: `startsAt − 15min` to `endsAt`. Lateness is derived on read, never stored.
- Officer door screen rendering the rotating code. Student scan page covering all six states from the prototype, with "code expired" worded as a normal retry and the no-signal state naming the manual override.
- Manual override by an officer, stored as `method: MANUAL`.

## Acceptance

- A token from two windows ago is rejected; from the previous window, accepted.
- A waitlisted Student cannot check in, and is told why kindly.
- A second scan reports "already checked in" and creates no second record.
- Scanned and manual records are always distinguishable.

## Tests

Fixed-clock codec tests at every window boundary. A tampered-HMAC rejection test. A concurrency test for duplicate scans. Integration tests for the Roster check and the window edges.
