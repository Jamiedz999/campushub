# CH-024 · Add the Waitlist, withdrawal and promotion

Sprint: 2
Area: event
Blocked by: 023
Decisions: [registration, capacity and Waitlist](../../adr/04-define-registration-capacity-and-waitlist.md), [student prototype](../prototypes/10-prototype-student-registration-and-checkin.md)

## Change

- Ordered `waitlist` array; joining is the same guarded write without the capacity guard.
- **Withdrawal and promotion are one aggregation-pipeline update**: `$filter` removes the withdrawing Student preserving order, `$slice` and `$concatArrays` move the queue head into `enrolled` with `via: PROMOTED`, and `promotedCount` increments in the same operation.
- Withdrawal is open until `startsAt`. Re-registering returns the Student to the tail.
- Promotion continues after the Registration Window closes and stops when the Event starts.
- Raising capacity promotes as many waiting Students as now fit, through the same operation.
- Student surfaces: waitlist position, the "you were on the waitlist — you're in" badge derived from `via`, and leave-the-waitlist.
- Officer surface: a warning before raising capacity naming how many will be admitted immediately.

## Acceptance

- **One freed Seat promotes exactly one Student, under parallel withdrawals.**
- A waitlisted Student withdrawing promotes nobody.
- `promotedCount` plus current waitlist length always equals everyone who ever queued.

## Tests

Concurrency test: parallel withdrawals against a full Event with a queue never over-promote. Fixed-clock tests for promotion after close and its stop at start. A capacity-raise test admitting several at once.
