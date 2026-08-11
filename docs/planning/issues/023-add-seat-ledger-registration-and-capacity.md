# CH-023 · Add the Seat Ledger — registration and capacity

Sprint: 2
Area: event
Blocked by: 022
Decisions: [registration, capacity and Waitlist](../../adr/04-define-registration-capacity-and-waitlist.md)

## Change

- `enrolled` becomes an array of `{ studentId, via, at }` on the Event document.
- Taking a Seat is **one `findAndModify`** whose filter carries the status check, both Registration Window comparisons, the duplicate guards and the `$expr` capacity guard. No read-then-write anywhere.
- On failure, one follow-up read classifies the reason — full, already enrolled, already waitlisted, window closed — for the message only.
- Student surfaces: Event page showing seats left, register action, "my events" list.

## Acceptance

- **Capacity is never exceeded under concurrent registration.** This is the project's central claim and this Issue is where it becomes true.
- A double-clicked registration is an idempotent no-op reporting "already registered".
- A losing writer changes nothing.

## Tests

**Concurrency test: N parallel registrations against a capacity of M produce exactly M enrolments**, run against Testcontainers MongoDB, not mocks. Fixed-clock tests for both Window boundaries. Frontend tests for each failure message.
